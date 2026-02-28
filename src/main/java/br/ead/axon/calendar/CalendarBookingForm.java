package br.ead.axon.calendar;

public class CalendarBookingForm {

    private String bookingId;
    private String anchorDate;
    private String startAt;
    private String endAt;

    private String locationType;
    private String locationValue;

    private String clinicianParticipantId;
    private String patientParticipantId;

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getAnchorDate() {
        return anchorDate;
    }

    public void setAnchorDate(String anchorDate) {
        this.anchorDate = anchorDate;
    }

    public String getStartAt() {
        return startAt;
    }

    public void setStartAt(String startAt) {
        this.startAt = startAt;
    }

    public String getEndAt() {
        return endAt;
    }

    public void setEndAt(String endAt) {
        this.endAt = endAt;
    }

    public String getLocationType() {
        return locationType;
    }

    public void setLocationType(String locationType) {
        this.locationType = locationType;
    }

    public String getLocationValue() {
        return locationValue;
    }

    public void setLocationValue(String locationValue) {
        this.locationValue = locationValue;
    }

    public String getClinicianParticipantId() {
        return clinicianParticipantId;
    }

    public void setClinicianParticipantId(String clinicianParticipantId) {
        this.clinicianParticipantId = clinicianParticipantId;
    }

    public String getPatientParticipantId() {
        return patientParticipantId;
    }

    public void setPatientParticipantId(String patientParticipantId) {
        this.patientParticipantId = patientParticipantId;
    }
}
