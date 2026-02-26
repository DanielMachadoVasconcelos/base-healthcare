package br.ead.axon.services;

import br.ead.axon.messages.events.AppointmentBookedEvent;
import br.ead.axon.messages.events.AppointmentCancelledEvent;
import br.ead.axon.messages.events.AppointmentConfirmedEvent;
import br.ead.axon.messages.events.AppointmentRescheduleEvent;
import br.ead.axon.model.querry.FindAllAppointmentQuery;
import br.ead.axon.model.entities.Appointment;
import br.ead.axon.model.entities.AppointmentStatus;
import br.ead.axon.repositories.AppointmentRepository;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
@AllArgsConstructor
public class AppointmentEventHandler {

    private final AppointmentRepository appointmentRepository;
    private final EntityManager entityManager;

    @EventHandler
    public void on(AppointmentBookedEvent event) {
        log.info("Handling the AppointmentBookedEvent [{}]", event);
        Appointment appointment = new Appointment(event.getAppointmentId(),
                event.getStartAt(),
                event.getEndAt(),
                event.getLocation(),
                event.getParticipants(),
                AppointmentStatus.CREATED);
        entityManager.persist(appointment);

        log.info("Persisted the Appointment [{}]", appointment);
    }

    @EventHandler
    public void on(AppointmentRescheduleEvent event) {
        log.info("Handling the AppointmentRescheduleEvent [{}]", event);
        String orderId = event.getAppointmentId();
        appointmentRepository.findById(orderId)
                .map(appointment -> appointment.withAppointmentStatus(AppointmentStatus.RESCHEDULED)
                        .withStartAt(event.getStartAt())
                        .withEndAt(event.getEndAt()))
                .map(appointmentRepository::save);
    }

    @EventHandler
    public void on(AppointmentCancelledEvent event) {
        log.info("Handling the AppointmentCancelledEvent [{}]", event);
        String orderId = event.getAppointmentId();
        appointmentRepository.findById(orderId)
                .map(appointment -> appointment.withAppointmentStatus(AppointmentStatus.CANCELLED))
                .map(appointmentRepository::save);
    }

    @EventHandler
    public void on(AppointmentConfirmedEvent event) {
        log.info("Handling the AppointmentConfirmedEvent [{}]", event);
        String orderId = event.getAppointmentId();
        appointmentRepository.findById(orderId)
                .map(appointment -> appointment.withAppointmentStatus(AppointmentStatus.CONFIRMED))
                .map(appointmentRepository::save);
    }

    @QueryHandler
    public List<Appointment> handle(FindAllAppointmentQuery query) {
        log.info("Handling a query request for FindAllOrderedProductsQuery [{}]", query);
        return appointmentRepository.findAppointmentByAppointmentStatusIsNot(AppointmentStatus.CANCELLED);
    }
}
