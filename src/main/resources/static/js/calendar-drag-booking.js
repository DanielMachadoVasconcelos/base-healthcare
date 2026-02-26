(function () {
    "use strict";

    var MINUTES_IN_DAY = 1440;
    var SNAP_MINUTES = 15;
    var MIN_DURATION_MINUTES = 30;
    var DRAG_THRESHOLD_MINUTES = 8;

    var state = {
        activeSelection: null,
        globalsBound: false
    };

    var dateFormatter = new Intl.DateTimeFormat(undefined, {
        weekday: "short",
        month: "short",
        day: "numeric"
    });

    var timeFormatter = new Intl.DateTimeFormat(undefined, {
        hour: "numeric",
        minute: "2-digit"
    });

    function initCalendarDragBooking() {
        bindGlobalListeners();

        var shell = document.getElementById("calendar-shell");
        if (!shell || shell.dataset.dragBookingReady === "1") {
            return;
        }

        shell.dataset.dragBookingReady = "1";

        var modal = shell.querySelector("[data-drag-booking-modal]");
        if (modal) {
            modal.addEventListener("click", function (event) {
                if (event.target === modal) {
                    closeModal(shell);
                }
            });
        }

        shell.querySelectorAll("[data-drag-booking-close]").forEach(function (button) {
            button.addEventListener("click", function () {
                closeModal(shell);
            });
        });

        shell.querySelectorAll(".day-column[data-iso-date]").forEach(function (column) {
            column.addEventListener("pointerdown", function (event) {
                beginSelection(event, shell, column);
            });
        });

        shell.querySelectorAll(".event-card[data-booking-id]").forEach(function (eventCard) {
            eventCard.addEventListener("pointerdown", function (event) {
                event.stopPropagation();
            });

            eventCard.addEventListener("click", function (event) {
                event.preventDefault();
                event.stopPropagation();
                openModalForEvent(shell, eventCard);
            });
        });

        wireModalFieldListeners(shell);
    }

    function wireModalFieldListeners(shell) {
        var startInput = shell.querySelector("[data-drag-start-input]");
        var endInput = shell.querySelector("[data-drag-end-input]");
        var locationTypeInput = shell.querySelector("[data-drag-location-type]");

        if (startInput) {
            startInput.addEventListener("change", function () {
                refreshSummary(shell);
            });
            startInput.addEventListener("input", function () {
                refreshSummary(shell);
            });
        }

        if (endInput) {
            endInput.addEventListener("change", function () {
                refreshSummary(shell);
            });
            endInput.addEventListener("input", function () {
                refreshSummary(shell);
            });
        }

        if (locationTypeInput) {
            locationTypeInput.addEventListener("change", function () {
                applyLocationFieldPresentation(shell, locationTypeInput.value);
            });
        }
    }

    function bindGlobalListeners() {
        if (state.globalsBound) {
            return;
        }

        state.globalsBound = true;

        document.addEventListener("pointermove", handlePointerMove);
        document.addEventListener("pointerup", handlePointerUp);
        document.addEventListener("pointercancel", clearActiveSelection);

        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape") {
                clearActiveSelection();
                var shell = document.getElementById("calendar-shell");
                if (shell) {
                    closeModal(shell);
                }
            }
        });

        document.addEventListener("DOMContentLoaded", initCalendarDragBooking);

        document.body.addEventListener("htmx:afterSwap", function () {
            initCalendarDragBooking();
        });
    }

    function beginSelection(event, shell, column) {
        if (event.button !== 0 || !event.isPrimary) {
            return;
        }

        if (event.target && typeof event.target.closest === "function" && event.target.closest(".event-card")) {
            return;
        }

        if (!column.dataset.isoDate) {
            return;
        }

        event.preventDefault();
        closeModal(shell);
        clearActiveSelection();

        var overlay = column.querySelector(".selection-preview");
        if (!overlay) {
            return;
        }

        var startRawMinute = positionToMinutes(column, event.clientY);
        state.activeSelection = {
            shell: shell,
            column: column,
            overlay: overlay,
            pointerId: event.pointerId,
            startRawMinute: startRawMinute,
            currentRawMinute: startRawMinute
        };

        column.classList.add("is-selecting");
        document.body.classList.add("is-dragging-selection");

        if (typeof column.setPointerCapture === "function") {
            try {
                column.setPointerCapture(event.pointerId);
            } catch (ignore) {
                // Some browsers/platforms may reject pointer capture here.
            }
        }

        renderPreview(state.activeSelection);
    }

    function handlePointerMove(event) {
        var active = state.activeSelection;
        if (!active || event.pointerId !== active.pointerId) {
            return;
        }

        active.currentRawMinute = positionToMinutes(active.column, event.clientY);
        renderPreview(active);
    }

    function handlePointerUp(event) {
        var active = state.activeSelection;
        if (!active || event.pointerId !== active.pointerId) {
            return;
        }

        var selection = buildSelection(active.startRawMinute, positionToMinutes(active.column, event.clientY), true);
        var shell = active.shell;
        var isoDate = active.column.dataset.isoDate;

        clearActiveSelection();
        if (isoDate) {
            openModalForSelection(shell, isoDate, selection);
        }
    }

    function renderPreview(active) {
        var selection = buildSelection(active.startRawMinute, active.currentRawMinute, false);
        var overlay = active.overlay;
        var topPct = (selection.startMinute / MINUTES_IN_DAY) * 100;
        var heightPct = ((selection.endMinute - selection.startMinute) / MINUTES_IN_DAY) * 100;

        overlay.hidden = false;
        overlay.style.top = topPct.toFixed(4) + "%";
        overlay.style.height = Math.max(heightPct, 0.5).toFixed(4) + "%";
    }

    function buildSelection(startRawMinute, endRawMinute, finalizeSelection) {
        var minRaw = Math.min(startRawMinute, endRawMinute);
        var maxRaw = Math.max(startRawMinute, endRawMinute);
        var hasDragged = Math.abs(endRawMinute - startRawMinute) >= DRAG_THRESHOLD_MINUTES;

        var startMinute = clamp(snapDown(minRaw), 0, MINUTES_IN_DAY - 1);
        var endMinute = clamp(snapUp(maxRaw), 1, MINUTES_IN_DAY);

        if (!hasDragged || (finalizeSelection && (endMinute - startMinute) < MIN_DURATION_MINUTES)) {
            endMinute = Math.min(MINUTES_IN_DAY, startMinute + MIN_DURATION_MINUTES);
        }

        if (endMinute <= startMinute) {
            endMinute = Math.min(MINUTES_IN_DAY, startMinute + SNAP_MINUTES);
        }

        return {
            startMinute: startMinute,
            endMinute: Math.min(MINUTES_IN_DAY, endMinute)
        };
    }

    function openModalForSelection(shell, isoDate, selection) {
        var sidebarDefaults = readSidebarCreateDefaults(shell);
        var startMinute = selection.startMinute;
        var endMinute = Math.min(MINUTES_IN_DAY - 1, Math.max(selection.endMinute, startMinute + SNAP_MINUTES));

        openModalWithValues(shell, {
            mode: "create",
            bookingId: "",
            anchorDate: isoDate,
            startAt: toDateTimeLocalValue(isoDate, startMinute),
            endAt: toDateTimeLocalValue(isoDate, endMinute),
            locationType: sidebarDefaults.locationType || "digital",
            locationValue: sidebarDefaults.locationValue || "",
            clinicianParticipantId: sidebarDefaults.clinicianParticipantId || "",
            patientParticipantId: sidebarDefaults.patientParticipantId || ""
        });
    }

    function openModalForEvent(shell, eventCard) {
        var bookingId = eventCard.dataset.bookingId || "";
        var startAt = eventCard.dataset.bookingStart || "";
        var endAt = eventCard.dataset.bookingEnd || "";

        if (!bookingId || !startAt || !endAt) {
            return;
        }

        openModalWithValues(shell, {
            mode: "edit",
            bookingId: bookingId,
            anchorDate: startAt.slice(0, 10),
            startAt: startAt,
            endAt: endAt,
            locationType: "digital",
            locationValue: "",
            clinicianParticipantId: "",
            patientParticipantId: ""
        });
    }

    function readSidebarCreateDefaults(shell) {
        var root = shell || document;
        var values = {
            locationType: "digital",
            locationValue: "",
            clinicianParticipantId: "",
            patientParticipantId: ""
        };

        var locationTypeField = root.querySelector(".booking-form [name='locationType']");
        var locationValueField = root.querySelector(".booking-form [name='locationValue']");
        var clinicianField = root.querySelector(".booking-form [name='clinicianParticipantId']");
        var patientField = root.querySelector(".booking-form [name='patientParticipantId']");

        if (locationTypeField) {
            values.locationType = locationTypeField.value || values.locationType;
        }
        if (locationValueField) {
            values.locationValue = locationValueField.value || values.locationValue;
        }
        if (clinicianField) {
            values.clinicianParticipantId = clinicianField.value || values.clinicianParticipantId;
        }
        if (patientField) {
            values.patientParticipantId = patientField.value || values.patientParticipantId;
        }

        return values;
    }

    function openModalWithValues(shell, payload) {
        var modal = shell.querySelector("[data-drag-booking-modal]");
        var form = shell.querySelector("[data-drag-booking-form]");
        if (!modal || !form) {
            return;
        }

        var bookingIdInput = form.querySelector("[data-drag-booking-id]");
        var anchorInput = form.querySelector("[data-drag-anchor-date]");
        var startInput = form.querySelector("[data-drag-start-input]");
        var endInput = form.querySelector("[data-drag-end-input]");
        var locationTypeInput = form.querySelector("[data-drag-location-type]");
        var locationValueInput = form.querySelector("[data-drag-location-value]");
        var clinicianInput = form.querySelector("[data-drag-clinician-id]");
        var patientInput = form.querySelector("[data-drag-patient-id]");
        var modeEyebrow = shell.querySelector("[data-drag-mode-eyebrow]");
        var modeTitle = shell.querySelector("[data-drag-mode-title]");
        var submitLabel = shell.querySelector("[data-drag-submit-label]");
        var createOnlyBlocks = shell.querySelectorAll("[data-drag-create-only]");
        var editNote = shell.querySelector("[data-drag-edit-note]");

        if (!bookingIdInput || !anchorInput || !startInput || !endInput) {
            return;
        }

        var isEdit = payload.mode === "edit";

        bookingIdInput.value = payload.bookingId || "";
        anchorInput.value = payload.anchorDate || "";
        startInput.value = payload.startAt || "";
        endInput.value = payload.endAt || "";

        if (locationTypeInput) {
            locationTypeInput.value = payload.locationType || "digital";
        }
        if (locationValueInput) {
            locationValueInput.value = payload.locationValue || "";
        }
        if (clinicianInput) {
            clinicianInput.value = payload.clinicianParticipantId || "";
        }
        if (patientInput) {
            patientInput.value = payload.patientParticipantId || "";
        }

        createOnlyBlocks.forEach(function (block) {
            block.hidden = isEdit;
            block.querySelectorAll("input, select, textarea").forEach(function (field) {
                field.disabled = isEdit;
            });
        });

        if (editNote) {
            editNote.hidden = !isEdit;
        }

        if (modeEyebrow) {
            modeEyebrow.textContent = isEdit ? "Edit booking" : "Quick booking";
        }
        if (modeTitle) {
            modeTitle.textContent = isEdit ? "Reschedule appointment" : "Create appointment from selected slot";
        }
        if (submitLabel) {
            submitLabel.textContent = isEdit ? "Reschedule" : "Create appointment";
        }

        applyLocationFieldPresentation(shell, locationTypeInput ? locationTypeInput.value : "digital");
        refreshSummary(shell);

        modal.hidden = false;
        requestAnimationFrame(function () {
            if (isEdit) {
                startInput.focus();
            } else if (locationValueInput) {
                locationValueInput.focus();
                locationValueInput.select();
            } else {
                startInput.focus();
            }
        });
    }

    function applyLocationFieldPresentation(shell, locationType) {
        var label = shell.querySelector("[data-drag-location-value-label]");
        var input = shell.querySelector("[data-drag-location-value]");
        var normalizedType = (locationType || "").toLowerCase();

        if (!label || !input) {
            return;
        }

        if (normalizedType === "physical") {
            label.textContent = "Address";
            input.placeholder = "123 Main St, Suite 200";
        } else {
            label.textContent = "Location URL";
            input.placeholder = "http://localhost:8181/live-room/...";
        }
    }

    function refreshSummary(shell) {
        var startInput = shell.querySelector("[data-drag-start-input]");
        var endInput = shell.querySelector("[data-drag-end-input]");
        var summaryEl = shell.querySelector("[data-drag-summary]");

        if (!startInput || !endInput || !summaryEl) {
            return;
        }

        summaryEl.textContent = buildSummaryFromInputValues(startInput.value, endInput.value);
    }

    function closeModal(shell) {
        var modal = shell.querySelector("[data-drag-booking-modal]");
        if (modal) {
            modal.hidden = true;
        }
    }

    function clearActiveSelection() {
        var active = state.activeSelection;
        if (!active) {
            return;
        }

        active.column.classList.remove("is-selecting");
        active.overlay.hidden = true;
        active.overlay.style.removeProperty("top");
        active.overlay.style.removeProperty("height");
        state.activeSelection = null;
        document.body.classList.remove("is-dragging-selection");
    }

    function positionToMinutes(column, clientY) {
        var rect = column.getBoundingClientRect();
        var y = clamp(clientY - rect.top, 0, rect.height);
        return (y / rect.height) * MINUTES_IN_DAY;
    }

    function toDateTimeLocalValue(isoDate, minuteOfDay) {
        var parts = isoDate.split("-").map(Number);
        var year = parts[0];
        var month = parts[1];
        var day = parts[2];

        var safeMinute = clamp(Math.round(minuteOfDay), 0, MINUTES_IN_DAY - 1);
        var hour = Math.floor(safeMinute / 60);
        var minute = safeMinute % 60;

        return [
            pad(year, 4), "-",
            pad(month, 2), "-",
            pad(day, 2), "T",
            pad(hour, 2), ":",
            pad(minute, 2)
        ].join("");
    }

    function buildSummaryFromInputValues(startValue, endValue) {
        var startDate = fromDateTimeLocalValue(startValue);
        var endDate = fromDateTimeLocalValue(endValue);

        if (!startDate || !endDate) {
            return "Select a time range on the calendar to start.";
        }

        return dateFormatter.format(startDate) + " • " + timeFormatter.format(startDate) + " - " + timeFormatter.format(endDate);
    }

    function fromDateTimeLocalValue(value) {
        if (!value || typeof value !== "string") {
            return null;
        }

        var match = value.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/);
        if (!match) {
            return null;
        }

        return new Date(
            Number(match[1]),
            Number(match[2]) - 1,
            Number(match[3]),
            Number(match[4]),
            Number(match[5]),
            0,
            0
        );
    }

    function snapDown(value) {
        return Math.floor(value / SNAP_MINUTES) * SNAP_MINUTES;
    }

    function snapUp(value) {
        return Math.ceil(value / SNAP_MINUTES) * SNAP_MINUTES;
    }

    function clamp(value, min, max) {
        return Math.min(max, Math.max(min, value));
    }

    function pad(value, size) {
        return String(value).padStart(size, "0");
    }

    initCalendarDragBooking();
})();
