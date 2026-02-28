package br.ead.axon.calendar;

import br.ead.axon.messages.commands.BookAppointmentCommand;
import br.ead.axon.messages.commands.RescheduleAppointmentCommand;
import br.ead.axon.model.api.ClinicianParticipant;
import br.ead.axon.model.api.DigitalLocation;
import br.ead.axon.model.api.Location;
import br.ead.axon.model.api.Participant;
import br.ead.axon.model.api.PatientParticipant;
import br.ead.axon.model.api.PhysicalLocation;
import br.ead.axon.model.entities.Appointment;
import br.ead.axon.model.entities.AppointmentStatus;
import br.ead.axon.model.querry.FindAllAppointmentQuery;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class CalendarAppointmentService {

    private static final DateTimeFormatter INPUT_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("h a", Locale.US);
    private static final DateTimeFormatter EVENT_TIME_LABEL = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final DateTimeFormatter DAY_OF_WEEK_LABEL = DateTimeFormatter.ofPattern("EEE", Locale.US);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    private final Clock clock = Clock.systemDefaultZone();
    private final ZoneId uiZone = ZoneId.systemDefault();

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    public CalendarAppointmentService(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public CalendarBookingForm defaultForm(LocalDate anchorDate) {
        var selectedDate = anchorDate != null ? anchorDate : today();
        var start = LocalDateTime.of(selectedDate, LocalTime.of(10, 0));
        var end = start.plusHours(1);

        CalendarBookingForm form = new CalendarBookingForm();
        form.setBookingId("");
        form.setAnchorDate(selectedDate.toString());
        form.setStartAt(start.format(INPUT_DATE_TIME));
        form.setEndAt(end.format(INPUT_DATE_TIME));
        form.setLocationType("digital");
        form.setLocationValue("http://localhost:8181/live-room/" + UUID.randomUUID());
        form.setClinicianParticipantId(UUID.randomUUID().toString());
        form.setPatientParticipantId(UUID.randomUUID().toString());
        return form;
    }

    public LocalDate resolveAnchorDate(String rawAnchorDate) {
        if (rawAnchorDate == null || rawAnchorDate.isBlank()) {
            return today();
        }

        try {
            return LocalDate.parse(rawAnchorDate);
        } catch (Exception ignored) {
            return today();
        }
    }

    public BookingSaveResult saveBooking(CalendarBookingForm form) {
        LocalDateTime startAtLocal = parseDateTime(form.getStartAt(), "Start time is invalid.");
        LocalDateTime endAtLocal = parseDateTime(form.getEndAt(), "End time is invalid.");
        validateTimeRange(startAtLocal, endAtLocal);

        String bookingId = normalize(form.getBookingId());
        boolean updated = !bookingId.isBlank();

        try {
            if (updated) {
                commandGateway.sendAndWait(new RescheduleAppointmentCommand(
                        bookingId,
                        startAtLocal.atZone(uiZone),
                        endAtLocal.atZone(uiZone)
                ));
            } else {
                Location location = buildLocation(form);
                Set<Participant> participants = buildParticipants(form);

                commandGateway.sendAndWait(new BookAppointmentCommand(
                        UUID.randomUUID().toString(),
                        startAtLocal.atZone(uiZone),
                        endAtLocal.atZone(uiZone),
                        location,
                        participants
                ));
            }
            return new BookingSaveResult(startAtLocal, updated);
        } catch (Exception ex) {
            throw new IllegalArgumentException(resolveUserMessage(ex));
        }
    }

    public CalendarWeekView buildWeek(LocalDate anchorDate) {
        var anchor = anchorDate != null ? anchorDate : today();
        var weekStart = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var weekEnd = weekStart.plusDays(6);
        var today = today();

        var visibleAppointments = fetchAppointments().stream()
                .map(this::toSlot)
                .filter(slot -> {
                    var slotDay = slot.startAt().toLocalDate();
                    return !slotDay.isBefore(weekStart) && !slotDay.isAfter(weekEnd);
                })
                .sorted(Comparator.comparing(AppointmentSlot::startAt).thenComparing(AppointmentSlot::endAt))
                .toList();

        var days = new ArrayList<DayColumnView>(7);
        for (int i = 0; i < 7; i++) {
            var date = weekStart.plusDays(i);
            var dayAppointments = visibleAppointments.stream()
                    .filter(slot -> slot.startAt().toLocalDate().equals(date))
                    .toList();

            days.add(new DayColumnView(
                    date.toString(),
                    DAY_OF_WEEK_LABEL.format(date).toUpperCase(Locale.US),
                    Integer.toString(date.getDayOfMonth()),
                    date.equals(today),
                    date.equals(anchor),
                    toEventViews(date, dayAppointments)
            ));
        }

        var hours = IntStream.range(0, 24)
                .mapToObj(hour -> new HourLabelView(hour, HOUR_LABEL.format(LocalTime.of(hour, 0))))
                .toList();

        return new CalendarWeekView(
                anchor.toString(),
                weekStart.toString(),
                weekEnd.toString(),
                formatWeekRangeLabel(weekStart, weekEnd),
                MONTH_LABEL.format(anchor),
                weekStart.minusWeeks(1).toString(),
                weekStart.plusWeeks(1).toString(),
                today.toString(),
                hours,
                days,
                buildMiniCalendar(weekStart, weekEnd, anchor, today)
        );
    }

    private List<Appointment> fetchAppointments() {
        return queryGateway.query(
                        new FindAllAppointmentQuery(),
                        ResponseTypes.multipleInstancesOf(Appointment.class)
                )
                .join();
    }

    private AppointmentSlot toSlot(Appointment appointment) {
        if (appointment.getStartAt() == null || appointment.getEndAt() == null) {
            throw new IllegalStateException("Appointment projection returned without start/end date.");
        }

        LocalDateTime startAt = appointment.getStartAt().withZoneSameInstant(uiZone).toLocalDateTime();
        LocalDateTime endAt = appointment.getEndAt().withZoneSameInstant(uiZone).toLocalDateTime();
        if (!endAt.isAfter(startAt)) {
            endAt = startAt.plusMinutes(15);
        }

        return new AppointmentSlot(
                appointment.getAppointmentId(),
                startAt,
                endAt,
                deriveDisplayTitle(appointment),
                appointment.getAppointmentStatus() == null ? AppointmentStatus.CREATED : appointment.getAppointmentStatus()
        );
    }

    private String deriveDisplayTitle(Appointment appointment) {
        String locationLabel = deriveLocationLabel(appointment.getLocation());
        String suffix = appointment.getAppointmentId() == null || appointment.getAppointmentId().isBlank()
                ? ""
                : " • " + appointment.getAppointmentId().substring(0, Math.min(6, appointment.getAppointmentId().length()));
        return locationLabel + suffix;
    }

    private String deriveLocationLabel(Location location) {
        if (location instanceof DigitalLocation) {
            return "Digital appointment";
        }
        if (location instanceof PhysicalLocation) {
            return "In-person appointment";
        }
        return "Appointment";
    }

    private Location buildLocation(CalendarBookingForm form) {
        String locationType = normalize(form.getLocationType()).toLowerCase(Locale.US);
        String locationValue = normalize(form.getLocationValue());

        if (locationValue.isBlank()) {
            throw new IllegalArgumentException("Location value is required.");
        }

        return switch (locationType) {
            case "physical" -> new PhysicalLocation(locationValue);
            case "digital", "" -> new DigitalLocation(locationValue);
            default -> throw new IllegalArgumentException("Location type is invalid.");
        };
    }

    private Set<Participant> buildParticipants(CalendarBookingForm form) {
        String clinicianId = normalize(form.getClinicianParticipantId());
        String patientId = normalize(form.getPatientParticipantId());

        if (clinicianId.isBlank()) {
            throw new IllegalArgumentException("Clinician participant id is required.");
        }
        if (patientId.isBlank()) {
            throw new IllegalArgumentException("Patient participant id is required.");
        }

        return Set.of(
                new ClinicianParticipant(clinicianId),
                new PatientParticipant(patientId)
        );
    }

    private void validateTimeRange(LocalDateTime startAt, LocalDateTime endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("End time must be after the start time.");
        }

        if (!startAt.toLocalDate().equals(endAt.toLocalDate())) {
            throw new IllegalArgumentException("The calendar week UI currently supports same-day appointments only.");
        }

        if (Duration.between(startAt, endAt).compareTo(Duration.ofMinutes(15)) < 0) {
            throw new IllegalArgumentException("Appointments must be at least 15 minutes long.");
        }
    }

    private String resolveUserMessage(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return "Unable to save appointment.";
    }

    private List<MiniWeekRowView> buildMiniCalendar(LocalDate weekStart, LocalDate weekEnd, LocalDate anchor, LocalDate today) {
        var monthStart = anchor.withDayOfMonth(1);
        var gridStart = monthStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        var rows = new ArrayList<MiniWeekRowView>(6);

        for (int row = 0; row < 6; row++) {
            var days = new ArrayList<MiniDayView>(7);
            for (int col = 0; col < 7; col++) {
                var date = gridStart.plusDays((long) row * 7 + col);
                days.add(new MiniDayView(
                        date.toString(),
                        Integer.toString(date.getDayOfMonth()),
                        date.getMonth().equals(monthStart.getMonth()),
                        date.equals(today),
                        !date.isBefore(weekStart) && !date.isAfter(weekEnd),
                        date.equals(anchor)
                ));
            }
            rows.add(new MiniWeekRowView(days));
        }

        return rows;
    }

    private List<EventView> toEventViews(LocalDate date, List<AppointmentSlot> dayAppointments) {
        if (dayAppointments.isEmpty()) {
            return List.of();
        }

        var laneEndTimes = new ArrayList<LocalDateTime>();
        var laneAssignments = new ArrayList<LaneAppointment>(dayAppointments.size());

        for (AppointmentSlot slot : dayAppointments) {
            int lane = findAvailableLane(laneEndTimes, slot.startAt());
            if (lane == laneEndTimes.size()) {
                laneEndTimes.add(slot.endAt());
            } else {
                laneEndTimes.set(lane, slot.endAt());
            }
            laneAssignments.add(new LaneAppointment(slot, lane));
        }

        int totalLanes = Math.max(1, laneEndTimes.size());
        var dayStart = date.atStartOfDay();

        return laneAssignments.stream()
                .map(item -> toEventView(dayStart, item, totalLanes))
                .toList();
    }

    private EventView toEventView(LocalDateTime dayStart, LaneAppointment item, int totalLanes) {
        var slot = item.slot();
        long startMinutes = Duration.between(dayStart, slot.startAt()).toMinutes();
        long durationMinutes = Duration.between(slot.startAt(), slot.endAt()).toMinutes();

        double topPercent = (Math.max(0, startMinutes) / 1440.0d) * 100.0d;
        double heightPercent = (Math.max(30, durationMinutes) / 1440.0d) * 100.0d;
        double widthPercent = 100.0d / totalLanes;
        double leftPercent = item.lane() * widthPercent;

        String statusLabel = humanizeStatus(slot.status());
        String timeLabel = slot.startAt().format(EVENT_TIME_LABEL)
                + " - " + slot.endAt().format(EVENT_TIME_LABEL)
                + " • " + statusLabel;

        return new EventView(
                slot.id(),
                slot.title(),
                timeLabel,
                slot.startAt().format(INPUT_DATE_TIME),
                slot.endAt().format(INPUT_DATE_TIME),
                colorForStatus(slot.status()),
                "top:" + formatPercent(topPercent)
                        + "%;height:" + formatPercent(heightPercent)
                        + "%;left:calc(" + formatPercent(leftPercent) + "% + 4px)"
                        + ";width:calc(" + formatPercent(widthPercent) + "% - 8px);"
        );
    }

    private String humanizeStatus(AppointmentStatus status) {
        return switch (status) {
            case CONFIRMED -> "Confirmed";
            case RESCHEDULED -> "Rescheduled";
            case CANCELLED -> "Cancelled";
            case CREATED -> "Created";
        };
    }

    private String colorForStatus(AppointmentStatus status) {
        return switch (status) {
            case CONFIRMED -> "green";
            case RESCHEDULED -> "amber";
            case CANCELLED -> "rose";
            case CREATED -> "blue";
        };
    }

    private int findAvailableLane(List<LocalDateTime> laneEndTimes, LocalDateTime nextStart) {
        for (int lane = 0; lane < laneEndTimes.size(); lane++) {
            if (!laneEndTimes.get(lane).isAfter(nextStart)) {
                return lane;
            }
        }
        return laneEndTimes.size();
    }

    private LocalDateTime parseDateTime(String raw, String errorMessage) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        try {
            return LocalDateTime.parse(raw, INPUT_DATE_TIME);
        } catch (Exception ex) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String formatPercent(double value) {
        return String.format(Locale.US, "%.4f", value);
    }

    private String formatWeekRangeLabel(LocalDate weekStart, LocalDate weekEnd) {
        if (weekStart.getYear() == weekEnd.getYear()) {
            if (weekStart.getMonth() == weekEnd.getMonth()) {
                return weekStart.getMonth().name().substring(0, 1)
                        + weekStart.getMonth().name().substring(1).toLowerCase(Locale.US)
                        + " " + weekStart.getDayOfMonth()
                        + " - " + weekEnd.getDayOfMonth()
                        + ", " + weekStart.getYear();
            }
            return formatMonthDay(weekStart) + " - " + formatMonthDay(weekEnd) + ", " + weekStart.getYear();
        }

        return formatMonthDay(weekStart) + ", " + weekStart.getYear()
                + " - " + formatMonthDay(weekEnd) + ", " + weekEnd.getYear();
    }

    private String formatMonthDay(LocalDate date) {
        var month = date.getMonth().name().substring(0, 1)
                + date.getMonth().name().substring(1).toLowerCase(Locale.US);
        return month + " " + date.getDayOfMonth();
    }

    private record AppointmentSlot(
            String id,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String title,
            AppointmentStatus status
    ) {
    }

    private record LaneAppointment(AppointmentSlot slot, int lane) {
    }

    public record CalendarWeekView(
            String anchorDateIso,
            String weekStartIso,
            String weekEndIso,
            String rangeLabel,
            String monthLabel,
            String previousWeekIso,
            String nextWeekIso,
            String todayIso,
            List<HourLabelView> hours,
            List<DayColumnView> days,
            List<MiniWeekRowView> miniWeeks
    ) {
    }

    public record HourLabelView(int hour, String label) {
    }

    public record DayColumnView(
            String isoDate,
            String weekdayLabel,
            String dayOfMonthLabel,
            boolean today,
            boolean selected,
            List<EventView> events
    ) {
    }

    public record EventView(
            String id,
            String title,
            String timeLabel,
            String startAtInputValue,
            String endAtInputValue,
            String colorToken,
            String style
    ) {
    }

    public record BookingSaveResult(LocalDateTime startAt, boolean updated) {
    }

    public record MiniWeekRowView(List<MiniDayView> days) {
    }

    public record MiniDayView(
            String isoDate,
            String dayOfMonthLabel,
            boolean currentMonth,
            boolean today,
            boolean inVisibleWeek,
            boolean selected
    ) {
    }
}
