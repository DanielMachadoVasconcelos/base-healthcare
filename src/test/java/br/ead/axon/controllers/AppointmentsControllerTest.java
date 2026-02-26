package br.ead.axon.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.ead.axon.SpringBootIntegrationTest;
import br.ead.axon.messages.commands.BookAppointmentCommand;
import br.ead.axon.model.api.ClinicianParticipant;
import br.ead.axon.model.api.DigitalLocation;
import br.ead.axon.model.api.PatientParticipant;
import br.ead.axon.model.requests.BookAppointmentRequest;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

class AppointmentsControllerTest extends SpringBootIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should be Able to book an appointment when the location is digital")
    void shouldBeAbleToBookAnAppointmentWhenTheLocationIsDigital() throws Exception {

        // given: A valid command to book an appointment with a digital location in the future
        ZonedDateTime today = ZonedDateTime.now(Clock.systemUTC());
        var command = new BookAppointmentRequest(today.plusDays(1l),
                today.plusDays(1l).plusHours(1l),
                new DigitalLocation("http://localhost:8181/live-room/%s".formatted(UUID.randomUUID().toString())),
                Set.of(new ClinicianParticipant(UUID.randomUUID().toString()),
                        new PatientParticipant(UUID.randomUUID().toString())
                )
        );

        // when: The endpoint is called with the command
        var response = mockMvc.perform(post("/api/v1/appointments/book")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("utf-8")
                .content(objectMapper.writeValueAsString(command)));

        // then: The response has the expected value
        response.andDo(print())
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.appointment_id").value(notNullValue()));

    }

}
