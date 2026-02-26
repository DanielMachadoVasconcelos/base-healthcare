# Healthcare Appointment Service
### Daniel Machado Vasconcelos
[![Java CI with Gradle](https://github.com/DanielMachadoVasconcelos/base-axon/actions/workflows/gradle.yml/badge.svg)](https://github.com/DanielMachadoVasconcelos/base-axon/actions/workflows/gradle.yml)

This repository proposal is illustrating the Event Sourcing with CQRS.
It is a healthcare service who coordinate booking appointments, enabling clients to book, cancel, confirm and reschedule medical appointment's.

### Basic requirements (that were implemented):
* Expose endpoints to book, cancel, confirm and reschedule appointments;
* The service should use event sourcing and CQRS.

### Extra requirements to be done:
* Add security layer to permit only authenticated users to operate;
* Add swagger documentation to the Rest API;

### Technical improvements to be done:
* Remove the Axon Server and replace it for Mongo Database;
* Add Message broker to publish events to external services;
* Expand the Query API to perform more fine grain queries.

---
Prerequisites
-------------

* Java JDK 25
* Gradle
* Docker / Docker Compose

#### Resources
* Axon
* PostgreSQL
