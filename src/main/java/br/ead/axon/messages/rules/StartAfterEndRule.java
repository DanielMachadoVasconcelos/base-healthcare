package br.ead.axon.messages.rules;

import br.ead.axon.exceptions.InvalidAppointmentPeriodException;
import br.ead.axon.messages.commands.BookAppointmentCommand;
import java.net.URI;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.core.BasicRule;
import org.springframework.http.ProblemDetail;

public class StartAfterEndRule extends BasicRule {

    public StartAfterEndRule() {
        super("StartAfterEndRule", "Appointment cannot start after it ends.", 1);
    }

    @Override
    public boolean evaluate(Facts facts) {
        BookAppointmentCommand command = facts.get("command");
        return command.getStartAt().isAfter(command.getEndAt());
    }

    @Override
    public void execute(Facts facts) {
        BookAppointmentCommand command = facts.get("command");
        ProblemDetail problemDetail = ProblemDetail.forStatus(404);
        problemDetail.setTitle("Invalid Appointment Period");
        problemDetail.setDetail("Period of date time is invalid. Appointment can not start after it ends.");
        problemDetail.setInstance(URI.create("/api/v1/appointments/book"));
        problemDetail.setProperty("appointmentId", command.getAppointmentId());
        problemDetail.setProperty("startAt", command.getStartAt());
        problemDetail.setProperty("endAt", command.getEndAt());
        problemDetail.setProperty("location", command.getLocation());
        problemDetail.setProperty("participants", command.getParticipants());
        throw new InvalidAppointmentPeriodException(problemDetail);
    }
}