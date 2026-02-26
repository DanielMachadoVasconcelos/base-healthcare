package br.ead.axon.calendar;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

@Service
public class CalendarPlaygroundService {

    private static final DateTimeFormatter INPUT_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("h a", Locale.US);
    private static final DateTimeFormatter EVENT_TIME_LABEL = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final DateTimeFormatter DAY_OF_WEEK_LABEL = DateTimeFormatter.ofPattern("EEE", Locale.US);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    private final Clock clock = Clock.systemDefaultZone();
    private final List<Booking> bookings = new CopyOnWriteArrayList<>();

    public CalendarPlaygroundService() {
        seedDemoBookings();
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
        form.setTitle("Follow-up visit");
        form.setStartAt(start.format(INPUT_DATE_TIME));
        form.setEndAt(end.format(INPUT_DATE_TIME));
        form.setColorToken("blue");
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
        var title = normalize(form.getTitle());
        if (title.isBlank()) {
            throw new IllegalArgumentException("A booking title is required.");
        }

        LocalDateTime startAt = parseDateTime(form.getStartAt(), "Start time is invalid.");
        LocalDateTime endAt = parseDateTime(form.getEndAt(), "End time is invalid.");

        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("End time must be after the start time.");
        }

        if (!startAt.toLocalDate().equals(endAt.toLocalDate())) {
            throw new IllegalArgumentException("This playground currently supports same-day bookings only.");
        }

        if (startAt.isBefore(today().minusMonths(12).atStartOfDay())
                || endAt.isAfter(today().plusMonths(12).plusDays(1).atStartOfDay())) {
            throw new IllegalArgumentException("Pick a time within +/- 12 months of today for the playground.");
        }

        String colorToken = normalize(form.getColorToken());
        if (colorToken.isBlank()) {
            colorToken = "blue";
        }

        UUID bookingId = parseBookingId(form.getBookingId());
        boolean updated = bookingId != null;
        if (updated) {
            UUID existingBookingId = bookingId;
            bookings.removeIf(existing -> existing.id().equals(existingBookingId));
        } else {
            bookingId = UUID.randomUUID();
        }

        bookings.add(new Booking(bookingId, title, startAt, endAt, colorToken));
        return new BookingSaveResult(startAt, updated);
    }

    public CalendarWeekView buildWeek(LocalDate anchorDate) {
        var anchor = anchorDate != null ? anchorDate : today();
        var weekStart = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var weekEnd = weekStart.plusDays(6);
        var today = today();

        var visibleBookings = bookings.stream()
                .filter(booking -> {
                    var bookingDay = booking.startAt().toLocalDate();
                    return !bookingDay.isBefore(weekStart) && !bookingDay.isAfter(weekEnd);
                })
                .sorted(Comparator.comparing(Booking::startAt).thenComparing(Booking::endAt))
                .toList();

        var days = new ArrayList<DayColumnView>(7);
        for (int i = 0; i < 7; i++) {
            var date = weekStart.plusDays(i);
            var dayBookings = visibleBookings.stream()
                    .filter(booking -> booking.startAt().toLocalDate().equals(date))
                    .toList();

            days.add(new DayColumnView(
                    date.toString(),
                    DAY_OF_WEEK_LABEL.format(date).toUpperCase(Locale.US),
                    Integer.toString(date.getDayOfMonth()),
                    date.equals(today),
                    date.equals(anchor),
                    toEventViews(date, dayBookings)
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

    private List<EventView> toEventViews(LocalDate date, List<Booking> dayBookings) {
        if (dayBookings.isEmpty()) {
            return List.of();
        }

        var laneEndTimes = new ArrayList<LocalDateTime>();
        var laneAssignments = new ArrayList<LaneBooking>(dayBookings.size());

        for (Booking booking : dayBookings) {
            int lane = findAvailableLane(laneEndTimes, booking.startAt());
            if (lane == laneEndTimes.size()) {
                laneEndTimes.add(booking.endAt());
            } else {
                laneEndTimes.set(lane, booking.endAt());
            }
            laneAssignments.add(new LaneBooking(booking, lane));
        }

        int totalLanes = Math.max(1, laneEndTimes.size());
        var dayStart = date.atStartOfDay();

        return laneAssignments.stream()
                .map(item -> toEventView(dayStart, item, totalLanes))
                .toList();
    }

    private EventView toEventView(LocalDateTime dayStart, LaneBooking item, int totalLanes) {
        var booking = item.booking();
        long startMinutes = java.time.Duration.between(dayStart, booking.startAt()).toMinutes();
        long durationMinutes = java.time.Duration.between(booking.startAt(), booking.endAt()).toMinutes();

        double topPercent = (Math.max(0, startMinutes) / 1440.0d) * 100.0d;
        double heightPercent = (Math.max(30, durationMinutes) / 1440.0d) * 100.0d;
        double widthPercent = 100.0d / totalLanes;
        double leftPercent = item.lane() * widthPercent;

        return new EventView(
                booking.id().toString(),
                booking.title(),
                booking.startAt().format(EVENT_TIME_LABEL) + " - " + booking.endAt().format(EVENT_TIME_LABEL),
                booking.startAt().format(INPUT_DATE_TIME),
                booking.endAt().format(INPUT_DATE_TIME),
                booking.colorToken(),
                "top:" + formatPercent(topPercent)
                        + "%;height:" + formatPercent(heightPercent)
                        + "%;left:calc(" + formatPercent(leftPercent) + "% + 4px)"
                        + ";width:calc(" + formatPercent(widthPercent) + "% - 8px);"
        );
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

    private UUID parseBookingId(String rawBookingId) {
        var normalized = normalize(rawBookingId);
        if (normalized.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Booking id is invalid.");
        }
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

    private void seedDemoBookings() {
        if (!bookings.isEmpty()) {
            return;
        }

        var monday = today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        bookings.add(new Booking(UUID.randomUUID(), "Dr. Silva intake", monday.plusDays(1).atTime(9, 0), monday.plusDays(1).atTime(10, 0), "blue"));
        bookings.add(new Booking(UUID.randomUUID(), "Vitals + nurse screening", monday.plusDays(1).atTime(9, 30), monday.plusDays(1).atTime(10, 15), "green"));
        bookings.add(new Booking(UUID.randomUUID(), "Telehealth check-in", monday.plusDays(2).atTime(13, 0), monday.plusDays(2).atTime(13, 45), "purple"));
        bookings.add(new Booking(UUID.randomUUID(), "Procedure room block", monday.plusDays(3).atTime(11, 0), monday.plusDays(3).atTime(12, 30), "amber"));
        bookings.add(new Booking(UUID.randomUUID(), "Specialist follow-up", monday.plusDays(4).atTime(15, 0), monday.plusDays(4).atTime(16, 0), "rose"));
        bookings.add(new Booking(UUID.randomUUID(), "New patient consult", monday.plusDays(4).atTime(15, 15), monday.plusDays(4).atTime(16, 15), "blue"));
    }

    private record Booking(UUID id, String title, LocalDateTime startAt, LocalDateTime endAt, String colorToken) {
    }

    private record LaneBooking(Booking booking, int lane) {
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
