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
                // Pointer capture may fail if browser/platform does not support it here.
            }
        }

        renderPreview(state.activeSelection, false);
    }

    function handlePointerMove(event) {
        var active = state.activeSelection;
        if (!active || event.pointerId !== active.pointerId) {
            return;
        }

        active.currentRawMinute = positionToMinutes(active.column, event.clientY);
        renderPreview(active, false);
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
            openModal(shell, isoDate, selection);
        }
    }

    function renderPreview(active, applyMinimumDuration) {
        var selection = buildSelection(active.startRawMinute, active.currentRawMinute, applyMinimumDuration);
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

        if (!hasDragged) {
            endMinute = Math.min(MINUTES_IN_DAY, startMinute + MIN_DURATION_MINUTES);
        }

        if (finalizeSelection && (endMinute - startMinute) < MIN_DURATION_MINUTES) {
            endMinute = Math.min(MINUTES_IN_DAY, startMinute + MIN_DURATION_MINUTES);
        }

        if (endMinute <= startMinute) {
            endMinute = Math.min(MINUTES_IN_DAY, startMinute + SNAP_MINUTES);
        }

        if (endMinute <= startMinute) {
            startMinute = Math.max(0, endMinute - SNAP_MINUTES);
        }

        return {
            startMinute: startMinute,
            endMinute: Math.min(MINUTES_IN_DAY, endMinute)
        };
    }

    function openModal(shell, isoDate, selection) {
        var modal = shell.querySelector("[data-drag-booking-modal]");
        var form = shell.querySelector("[data-drag-booking-form]");
        if (!modal || !form) {
            return;
        }

        var anchorInput = form.querySelector("[data-drag-anchor-date]");
        var titleInput = form.querySelector("[data-drag-title-input]");
        var startInput = form.querySelector("[data-drag-start-input]");
        var endInput = form.querySelector("[data-drag-end-input]");
        var colorInput = form.querySelector("[data-drag-color-input]");
        var summaryEl = shell.querySelector("[data-drag-summary]");

        if (!anchorInput || !startInput || !endInput || !summaryEl) {
            return;
        }

        anchorInput.value = isoDate;
        startInput.value = toDateTimeLocalValue(isoDate, selection.startMinute);

        var finalEndMinute = Math.min(MINUTES_IN_DAY - 1, selection.endMinute);
        if (finalEndMinute <= selection.startMinute) {
            finalEndMinute = Math.min(MINUTES_IN_DAY - 1, selection.startMinute + SNAP_MINUTES);
        }
        endInput.value = toDateTimeLocalValue(isoDate, finalEndMinute);

        if (titleInput && !titleInput.value.trim()) {
            titleInput.value = "Follow-up visit";
        }

        if (colorInput && !colorInput.value) {
            colorInput.value = "blue";
        }

        summaryEl.textContent = buildSummary(isoDate, selection.startMinute, finalEndMinute);

        modal.hidden = false;

        if (titleInput) {
            requestAnimationFrame(function () {
                titleInput.focus();
                titleInput.select();
            });
        }
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

    function buildSummary(isoDate, startMinute, endMinute) {
        var startDate = toDate(isoDate, startMinute);
        var endDate = toDate(isoDate, endMinute);
        return dateFormatter.format(startDate) + " • " + timeFormatter.format(startDate) + " - " + timeFormatter.format(endDate);
    }

    function toDate(isoDate, minuteOfDay) {
        var parts = isoDate.split("-").map(Number);
        var date = new Date(parts[0], parts[1] - 1, parts[2], 0, 0, 0, 0);
        date.setMinutes(clamp(Math.round(minuteOfDay), 0, MINUTES_IN_DAY - 1));
        return date;
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
