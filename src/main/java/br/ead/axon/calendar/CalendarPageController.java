package br.ead.axon.calendar;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequestMapping("/calendar")
public class CalendarPageController {

    private final CalendarPlaygroundService calendarPlaygroundService;

    public CalendarPageController(CalendarPlaygroundService calendarPlaygroundService) {
        this.calendarPlaygroundService = calendarPlaygroundService;
    }

    @GetMapping
    public String calendarPage(@RequestParam(value = "date", required = false) LocalDate date,
                               Model model) {
        LocalDate anchor = date != null ? date : calendarPlaygroundService.today();
        populatePageModel(model, anchor, calendarPlaygroundService.defaultForm(anchor), null, null);
        return "calendar/index";
    }

    @GetMapping("/week")
    public String calendarWeekFragment(@RequestParam(value = "date", required = false) LocalDate date,
                                       Model model) {
        LocalDate anchor = date != null ? date : calendarPlaygroundService.today();
        populatePageModel(model, anchor, calendarPlaygroundService.defaultForm(anchor), null, null);
        return "calendar/index :: calendarShell";
    }

    @PostMapping("/bookings")
    public String createBooking(@ModelAttribute("bookingForm") CalendarBookingForm bookingForm,
                                @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                HttpServletResponse response,
                                Model model) {
        LocalDate anchor = calendarPlaygroundService.resolveAnchorDate(bookingForm.getAnchorDate());
        String successMessage = null;
        String errorMessage = null;

        try {
            var saveResult = calendarPlaygroundService.saveBooking(bookingForm);
            anchor = saveResult.startAt().toLocalDate();
            successMessage = saveResult.updated()
                    ? "Booking updated in the calendar playground."
                    : "Booking added to the calendar playground.";
            bookingForm = calendarPlaygroundService.defaultForm(anchor);
        } catch (IllegalArgumentException ex) {
            response.setStatus(422);
            errorMessage = ex.getMessage();
        }

        populatePageModel(model, anchor, bookingForm, successMessage, errorMessage);
        return isHtmx(hxRequest) ? "calendar/index :: calendarShell" : "calendar/index";
    }

    private void populatePageModel(Model model,
                                   LocalDate anchorDate,
                                   CalendarBookingForm bookingForm,
                                   String successMessage,
                                   String errorMessage) {
        model.addAttribute("vm", calendarPlaygroundService.buildWeek(anchorDate));
        model.addAttribute("bookingForm", bookingForm);
        model.addAttribute("successMessage", successMessage);
        model.addAttribute("errorMessage", errorMessage);
    }

    private boolean isHtmx(String hxRequest) {
        return hxRequest != null && !hxRequest.isBlank();
    }
}
