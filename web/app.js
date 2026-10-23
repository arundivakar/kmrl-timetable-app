// KMRL Train Finder - Progressive Web App (PWA) Engine
(() => {
  'use strict';

  const FIREBASE_RTDB_BASE = 'https://kmrl-train-finder-default-rtdb.asia-southeast1.firebasedatabase.app';
  
  // Application State
  const state = {
    stations: [],
    timetablesMeta: {},
    dayDefaults: {},
    dateAssignments: {},
    loadedTimetables: {}, // cache of { timetableName: data }
    
    // Route Search
    selectedFromStation: null,
    selectedToStation: null,
    selectedTravelDate: new Date(),
    routeFilterMode: 'UPCOMING', // 'UPCOMING' | 'ALL'

    // Station Timings Tab
    selectedSingleStation: null,
    stationDay: 'TODAY', // 'TODAY' | 'TOMORROW'
    stationDirection: 'UP', // 'UP' = towards TPHT, 'DOWN' = towards Aluva
    stationFilterMode: 'UPCOMING', // 'UPCOMING' | 'ALL'

    // Calendar Picker State
    calendarViewYear: new Date().getFullYear(),
    calendarViewMonth: new Date().getMonth(),
    calendarSelectedDate: new Date(),

    // System & Navigation
    activeTimetableName: '',
    currentDate: new Date(),
    isDarkMode: false,
    logoTapCount: 0,
    logoTapTimeout: null
  };

  // ---------------------------------------------------------------------------
  // Initialization
  // ---------------------------------------------------------------------------
  document.addEventListener('DOMContentLoaded', async () => {
    initTheme();
    initClock();
    initNavigation();
    initDateSelector();
    initStationPickerModal();
    initTrainStopsModal();
    initReleasesModal();
    initInfoModal();
    initIosPrompt();
    initLogoAdminGesture();
    registerServiceWorker();

    try {
      await loadInitialData();
      await syncFirebaseDefaults();
      setDefaultStations();
      renderAll();

      // Auto-sync with Firebase when app resumes or regains focus
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') {
          syncFirebaseDefaults().then(() => renderAll());
        }
      });
      window.addEventListener('focus', () => {
        syncFirebaseDefaults().then(() => renderAll());
      });

      // Periodic background sync every 2 minutes
      setInterval(() => {
        syncFirebaseDefaults().then(() => renderAll());
      }, 120000);
    } catch (err) {
      console.error('Initialization error:', err);
    }
  });

  // ---------------------------------------------------------------------------
  // Service Worker Registration & Auto-Reload on Update
  // ---------------------------------------------------------------------------
  function registerServiceWorker() {
    if ('serviceWorker' in navigator) {
      // Reload page if a new service worker takes control (evicts old cache immediately)
      let refreshing = false;
      navigator.serviceWorker.addEventListener('controllerchange', () => {
        if (!refreshing) {
          refreshing = true;
          window.location.reload();
        }
      });

      navigator.serviceWorker.register('sw.js')
        .then((reg) => {
          console.log('KMRL Service Worker active:', reg.scope);
          const badge = document.getElementById('offline-status-badge');
          if (badge) badge.textContent = '✅ Active (Cache Ready)';

          // Check for service worker updates immediately
          reg.update();
        })
        .catch((err) => {
          console.warn('Service Worker registration skipped/failed:', err);
        });
    }
  }

  // ---------------------------------------------------------------------------
  // Theme Management
  // ---------------------------------------------------------------------------
  function initTheme() {
    const saved = localStorage.getItem('kmrl_theme');
    const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    state.isDarkMode = saved ? saved === 'dark' : prefersDark;
    applyTheme();

    const btnTheme = document.getElementById('btn-theme');
    if (btnTheme) {
      btnTheme.addEventListener('click', () => {
        state.isDarkMode = !state.isDarkMode;
        localStorage.setItem('kmrl_theme', state.isDarkMode ? 'dark' : 'light');
        applyTheme();
      });
    }
  }

  function applyTheme() {
    const body = document.body;
    const iconMoon = document.getElementById('theme-icon-moon');
    const iconSun = document.getElementById('theme-icon-sun');

    if (state.isDarkMode) {
      body.classList.remove('theme-light');
      body.classList.add('theme-dark');
      if (iconMoon) iconMoon.classList.add('hidden');
      if (iconSun) iconSun.classList.remove('hidden');
    } else {
      body.classList.remove('theme-dark');
      body.classList.add('theme-light');
      if (iconMoon) iconMoon.classList.remove('hidden');
      if (iconSun) iconSun.classList.add('hidden');
    }
  }

  // ---------------------------------------------------------------------------
  // Live Clock
  // ---------------------------------------------------------------------------
  function initClock() {
    const dateElem = document.getElementById('status-date');
    const timeElem = document.getElementById('status-time');

    function update() {
      state.currentDate = new Date();
      const options = { weekday: 'short', day: 'numeric', month: 'short' };
      if (dateElem) dateElem.textContent = state.currentDate.toLocaleDateString('en-US', options);
      if (timeElem) timeElem.textContent = state.currentDate.toLocaleTimeString('en-US', { hour12: false });
    }

    update();
    setInterval(() => {
      update();
      updateCountdowns();
    }, 1000);
  }

  // ---------------------------------------------------------------------------
  // Data Loading & Firebase Sync
  // ---------------------------------------------------------------------------
  async function loadInitialData() {
    // 1. Stations
    const stationsRes = await fetch('data/stations.json');
    state.stations = await stationsRes.json();

    // 2. Timetables Metadata & Defaults
    const ttRes = await fetch('data/timetables.json');
    const ttJson = await ttRes.json();
    state.dayDefaults = ttJson.defaults || {};
    state.timetablesMeta = (ttJson.timetables || []).reduce((acc, item) => {
      acc[item.name] = item;
      return acc;
    }, {});

    const countElem = document.getElementById('info-schedules-count');
    if (countElem) {
      countElem.textContent = `${(ttJson.timetables || []).length} Timetables Loaded`;
    }
  }

  async function syncFirebaseDefaults() {
    try {
      // Fetch day defaults (0-6)
      const defRes = await fetch(`${FIREBASE_RTDB_BASE}/day_defaults.json`, { cache: 'no-store' });
      if (defRes.ok) {
        const defData = await defRes.json();
        if (defData && typeof defData === 'object') {
          Object.keys(defData).forEach((key) => {
            state.dayDefaults[parseInt(key, 10)] = defData[key];
          });
        }
      }

      // Fetch date assignments
      const dateRes = await fetch(`${FIREBASE_RTDB_BASE}/date_assignments.json`, { cache: 'no-store' });
      if (dateRes.ok) {
        const dateData = await dateRes.json();
        if (dateData && typeof dateData === 'object') {
          state.dateAssignments = dateData;
        }
      }
    } catch (e) {
      console.warn('Using offline/bundled defaults (Firebase unavailable):', e.message);
    }
  }

  function getTimetableNameForDate(date) {
    if (!date) date = new Date();
    const yyyy = date.getFullYear();
    const mm = String(date.getMonth() + 1).padStart(2, '0');
    const dd = String(date.getDate()).padStart(2, '0');
    const dateStr = `${yyyy}-${mm}-${dd}`;

    if (state.dateAssignments[dateStr]) {
      return state.dateAssignments[dateStr];
    }

    // Python day_of_week index: 0 = Mon, ..., 6 = Sun
    const jsDay = date.getDay(); // 0 = Sun, 1 = Mon ...
    const pythonDay = (jsDay + 6) % 7;

    if (pythonDay === 6) {
      return state.dayDefaults[6] || '13S111026_MRP1';
    } else {
      return state.dayDefaults[0] || state.dayDefaults[pythonDay] || '16W070926_TPHTOFFPEAK_MRP1';
    }
  }

  async function getTimetableData(timetableName) {
    if (!timetableName) timetableName = '16W070926_TPHTOFFPEAK_MRP1';
    if (state.loadedTimetables[timetableName]) {
      return state.loadedTimetables[timetableName];
    }

    try {
      const safeFilename = timetableName.replace(/[^a-zA-Z0-9_\-\.]/g, '') + '.json';
      const res = await fetch(`data/timetables/${safeFilename}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      state.loadedTimetables[timetableName] = data;
      return data;
    } catch (err) {
      console.warn(`Could not load ${timetableName}, falling back to default MRP1:`, err);
      if (timetableName !== '16W070926_TPHTOFFPEAK_MRP1') {
        return getTimetableData('16W070926_TPHTOFFPEAK_MRP1');
      }
      return null;
    }
  }

  function setDefaultStations() {
    if (state.stations.length >= 2) {
      state.selectedFromStation = state.stations[0]; // Aluva (#1)
      state.selectedToStation = state.stations[state.stations.length - 1]; // Tripunithura (#25)
      state.selectedSingleStation = state.stations[0]; // Aluva
    }
  }

  // ---------------------------------------------------------------------------
  // Fare Calculation & Route Analytics
  // ---------------------------------------------------------------------------
  function calculateFare(fromStation, toStation) {
    if (!fromStation || !toStation) return { fare: 0, stations: 0, distance: '0.0' };
    const stationCount = Math.abs(fromStation.sequence - toStation.sequence);
    if (stationCount === 0) return { fare: 0, stations: 0, distance: '0.0' };

    let fare = 60;
    if (stationCount <= 2) fare = 10;
    else if (stationCount <= 5) fare = 20;
    else if (stationCount <= 9) fare = 30;
    else if (stationCount <= 14) fare = 40;
    else if (stationCount <= 18) fare = 50;

    const distance = (stationCount * 1.17).toFixed(1);
    return { fare, stations: stationCount, distance };
  }

  // ---------------------------------------------------------------------------
  // Navigation & Tabs
  // ---------------------------------------------------------------------------
  function initNavigation() {
    const navButtons = document.querySelectorAll('.nav-item');
    navButtons.forEach((btn) => {
      btn.addEventListener('click', () => {
        navButtons.forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');

        const targetId = btn.getAttribute('data-tab');
        document.querySelectorAll('.tab-pane').forEach((pane) => {
          pane.classList.remove('active');
        });
        const targetPane = document.getElementById(targetId);
        if (targetPane) targetPane.classList.add('active');

        renderAll();
      });
    });
  }

  // ---------------------------------------------------------------------------
  // Dedicated Calendar Date Picker (Modal & Full Date Selection)
  // ---------------------------------------------------------------------------
  function initDateSelector() {
    const chipToday = document.getElementById('chip-date-today');
    const chipTomorrow = document.getElementById('chip-date-tomorrow');
    const chipCustom = document.getElementById('chip-date-custom');
    const customBtnText = document.getElementById('custom-date-btn-text');
    const dateDisplay = document.getElementById('active-date-display');

    // Calendar Modal elements
    const calModal = document.getElementById('modal-date-picker');
    const calCloseBtn = document.getElementById('btn-close-date-picker');
    const calPrevBtn = document.getElementById('btn-cal-prev');
    const calNextBtn = document.getElementById('btn-cal-next');
    const calMonthYearLabel = document.getElementById('cal-month-year-label');
    const calDaysGrid = document.getElementById('calendar-days-grid');
    const calPreviewDateStr = document.getElementById('cal-preview-date-str');
    const calPreviewTtName = document.getElementById('cal-preview-tt-name');
    const btnApplyPickedDate = document.getElementById('btn-apply-picked-date');
    const btnPresetToday = document.getElementById('btn-preset-today');
    const btnPresetTomorrow = document.getElementById('btn-preset-tomorrow');
    const btnPresetSunday = document.getElementById('btn-preset-sunday');

    function applySelectedDate(date, mode) {
      state.selectedTravelDate = new Date(date);
      if (chipToday) chipToday.classList.toggle('active', mode === 'today');
      if (chipTomorrow) chipTomorrow.classList.toggle('active', mode === 'tomorrow');
      if (chipCustom) chipCustom.classList.toggle('active', mode === 'custom');

      const isToday = isSameDay(date, state.currentDate);
      const tmrwDate = new Date();
      tmrwDate.setDate(tmrwDate.getDate() + 1);
      const isTomorrow = isSameDay(date, tmrwDate);

      if (customBtnText) {
        if (mode === 'custom') {
          customBtnText.textContent = `📅 ${date.toLocaleDateString('en-US', { day: 'numeric', month: 'short' })}`;
        } else {
          customBtnText.textContent = 'PICK DATE';
        }
      }

      if (dateDisplay) {
        if (isToday) {
          dateDisplay.textContent = 'Today (' + date.toLocaleDateString('en-US', { day: 'numeric', month: 'short' }) + ')';
        } else if (isTomorrow) {
          dateDisplay.textContent = 'Tomorrow (' + date.toLocaleDateString('en-US', { day: 'numeric', month: 'short' }) + ')';
        } else {
          dateDisplay.textContent = date.toLocaleDateString('en-US', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
        }
      }

      renderTodaySearch();
      renderFullTimetable();
    }

    if (chipToday) {
      chipToday.addEventListener('click', () => {
        applySelectedDate(new Date(), 'today');
      });
    }

    if (chipTomorrow) {
      chipTomorrow.addEventListener('click', () => {
        const tmrw = new Date();
        tmrw.setDate(tmrw.getDate() + 1);
        applySelectedDate(tmrw, 'tomorrow');
      });
    }

    // Open Calendar Modal
    if (chipCustom && calModal) {
      chipCustom.addEventListener('click', () => {
        state.calendarSelectedDate = new Date(state.selectedTravelDate);
        state.calendarViewYear = state.calendarSelectedDate.getFullYear();
        state.calendarViewMonth = state.calendarSelectedDate.getMonth();
        renderCalendarView();
        calModal.classList.remove('hidden');
      });
    }

    if (calCloseBtn && calModal) {
      calCloseBtn.addEventListener('click', () => calModal.classList.add('hidden'));
      calModal.addEventListener('click', (e) => {
        if (e.target === calModal) calModal.classList.add('hidden');
      });
    }

    if (calPrevBtn) {
      calPrevBtn.addEventListener('click', () => {
        state.calendarViewMonth--;
        if (state.calendarViewMonth < 0) {
          state.calendarViewMonth = 11;
          state.calendarViewYear--;
        }
        renderCalendarView();
      });
    }

    if (calNextBtn) {
      calNextBtn.addEventListener('click', () => {
        state.calendarViewMonth++;
        if (state.calendarViewMonth > 11) {
          state.calendarViewMonth = 0;
          state.calendarViewYear++;
        }
        renderCalendarView();
      });
    }

    // Presets inside modal
    if (btnPresetToday) {
      btnPresetToday.addEventListener('click', () => {
        state.calendarSelectedDate = new Date();
        state.calendarViewYear = state.calendarSelectedDate.getFullYear();
        state.calendarViewMonth = state.calendarSelectedDate.getMonth();
        renderCalendarView();
      });
    }

    if (btnPresetTomorrow) {
      btnPresetTomorrow.addEventListener('click', () => {
        const tmrw = new Date();
        tmrw.setDate(tmrw.getDate() + 1);
        state.calendarSelectedDate = tmrw;
        state.calendarViewYear = state.calendarSelectedDate.getFullYear();
        state.calendarViewMonth = state.calendarSelectedDate.getMonth();
        renderCalendarView();
      });
    }

    if (btnPresetSunday) {
      btnPresetSunday.addEventListener('click', () => {
        const d = new Date();
        const currentDay = d.getDay(); // 0 is Sun
        const daysUntilSunday = currentDay === 0 ? 7 : (7 - currentDay);
        d.setDate(d.getDate() + daysUntilSunday);
        state.calendarSelectedDate = d;
        state.calendarViewYear = state.calendarSelectedDate.getFullYear();
        state.calendarViewMonth = state.calendarSelectedDate.getMonth();
        renderCalendarView();
      });
    }

    if (btnApplyPickedDate && calModal) {
      btnApplyPickedDate.addEventListener('click', () => {
        const isToday = isSameDay(state.calendarSelectedDate, state.currentDate);
        const tmrwDate = new Date();
        tmrwDate.setDate(tmrwDate.getDate() + 1);
        const isTomorrow = isSameDay(state.calendarSelectedDate, tmrwDate);

        const mode = isToday ? 'today' : (isTomorrow ? 'tomorrow' : 'custom');
        applySelectedDate(state.calendarSelectedDate, mode);
        calModal.classList.add('hidden');
      });
    }

    function renderCalendarView() {
      const year = state.calendarViewYear;
      const month = state.calendarViewMonth;

      const monthName = new Date(year, month, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
      if (calMonthYearLabel) calMonthYearLabel.textContent = monthName;

      // Update Preview Card inside modal
      if (calPreviewDateStr && calPreviewTtName) {
        calPreviewDateStr.textContent = state.calendarSelectedDate.toLocaleDateString('en-US', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
        const ttName = getTimetableNameForDate(state.calendarSelectedDate);
        calPreviewTtName.textContent = `Schedule: ${ttName}`;
      }

      if (!calDaysGrid) return;
      calDaysGrid.innerHTML = '';

      // First day of month (0 = Sun .. 6 = Sat). Convert to Monday-first (0 = Mon .. 6 = Sun)
      const firstDay = new Date(year, month, 1).getDay();
      const startingDayIdx = (firstDay + 6) % 7;
      const daysInMonth = new Date(year, month + 1, 0).getDate();
      const prevMonthDays = new Date(year, month, 0).getDate();

      // Leading previous month cells
      for (let i = startingDayIdx - 1; i >= 0; i--) {
        const dayNum = prevMonthDays - i;
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'cal-day-cell other-month';
        btn.textContent = dayNum;
        btn.addEventListener('click', () => {
          state.calendarViewMonth--;
          if (state.calendarViewMonth < 0) {
            state.calendarViewMonth = 11;
            state.calendarViewYear--;
          }
          state.calendarSelectedDate = new Date(state.calendarViewYear, state.calendarViewMonth, dayNum);
          renderCalendarView();
        });
        calDaysGrid.appendChild(btn);
      }

      // Current month days
      for (let d = 1; d <= daysInMonth; d++) {
        const thisDate = new Date(year, month, d);
        const isToday = isSameDay(thisDate, state.currentDate);
        const isSelected = isSameDay(thisDate, state.calendarSelectedDate);
        const isSunday = thisDate.getDay() === 0;

        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = `cal-day-cell ${isToday ? 'today' : ''} ${isSelected ? 'selected' : ''} ${isSunday ? 'sunday' : ''}`;
        
        const ttName = getTimetableNameForDate(thisDate);
        const isFestival = ttName.toUpperCase().includes('FEST');
        const tag = isSunday ? 'SUN' : (isFestival ? 'FEST' : 'WD');

        btn.innerHTML = `
          <span>${d}</span>
          <span class="cal-day-tag">${tag}</span>
        `;

        btn.addEventListener('click', () => {
          state.calendarSelectedDate = new Date(year, month, d);
          renderCalendarView();
        });

        calDaysGrid.appendChild(btn);
      }

      // Trailing next month cells to fill grid (35 or 42 cells)
      const totalCells = startingDayIdx + daysInMonth;
      const remaining = totalCells <= 35 ? (35 - totalCells) : (42 - totalCells);
      for (let d = 1; d <= remaining; d++) {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'cal-day-cell other-month';
        btn.textContent = d;
        btn.addEventListener('click', () => {
          state.calendarViewMonth++;
          if (state.calendarViewMonth > 11) {
            state.calendarViewMonth = 0;
            state.calendarViewYear++;
          }
          state.calendarSelectedDate = new Date(state.calendarViewYear, state.calendarViewMonth, d);
          renderCalendarView();
        });
        calDaysGrid.appendChild(btn);
      }
    }

    // Default to Today on launch
    applySelectedDate(new Date(), 'today');
  }

  // ---------------------------------------------------------------------------
  // Station Picker Modal & Quick Pills
  // ---------------------------------------------------------------------------
  let pickerCallback = null;

  function initStationPickerModal() {
    const modal = document.getElementById('modal-station-picker');
    const closeBtn = document.getElementById('btn-close-modal');
    const searchInput = document.getElementById('station-search-input');
    const clearBtn = document.getElementById('btn-clear-station-search');
    const listContainer = document.getElementById('station-list-items');
    const quickChips = document.getElementById('modal-quick-chips');

    if (closeBtn && modal) closeBtn.addEventListener('click', () => modal.classList.add('hidden'));
    if (modal) {
      modal.addEventListener('click', (e) => {
        if (e.target === modal) modal.classList.add('hidden');
      });
    }

    if (searchInput) {
      searchInput.addEventListener('input', () => {
        const q = searchInput.value.toLowerCase().trim();
        if (clearBtn) clearBtn.classList.toggle('hidden', q.length === 0);
        renderStationModalList(q);
      });
    }

    if (clearBtn && searchInput) {
      clearBtn.addEventListener('click', () => {
        searchInput.value = '';
        clearBtn.classList.add('hidden');
        renderStationModalList('');
        searchInput.focus();
      });
    }

    // Quick pick chips inside modal
    if (quickChips) {
      quickChips.querySelectorAll('.quick-chip').forEach((chip) => {
        chip.addEventListener('click', () => {
          const stId = parseInt(chip.getAttribute('data-station-id'), 10);
          const station = state.stations.find((s) => s.id === stId);
          if (station && pickerCallback && modal) {
            pickerCallback(station);
            modal.classList.add('hidden');
          }
        });
      });
    }

    function renderStationModalList(filter = '') {
      if (!listContainer) return;
      listContainer.innerHTML = '';
      state.stations
        .filter((s) => s.name.toLowerCase().includes(filter) || s.code.toLowerCase().includes(filter))
        .forEach((st) => {
          const item = document.createElement('div');
          item.className = 'station-item';
          item.innerHTML = `
            <span class="station-item-name">${st.name} (${st.code})</span>
            <span class="station-item-seq">#${st.sequence}</span>
          `;
          item.addEventListener('click', () => {
            if (pickerCallback) pickerCallback(st);
            if (modal) modal.classList.add('hidden');
          });
          listContainer.appendChild(item);
        });
    }

    window.openStationPicker = (title, onSelect) => {
      const modalTitle = document.getElementById('modal-station-title');
      if (modalTitle) modalTitle.textContent = title;
      if (searchInput) searchInput.value = '';
      if (clearBtn) clearBtn.classList.add('hidden');
      pickerCallback = onSelect;
      renderStationModalList();
      if (modal) {
        modal.classList.remove('hidden');
        setTimeout(() => searchInput && searchInput.focus(), 150);
      }
    };

    // Attach to UI buttons
    const btnSelectFrom = document.getElementById('btn-select-from');
    if (btnSelectFrom) {
      btnSelectFrom.addEventListener('click', () => {
        window.openStationPicker('Select Source Station', (st) => {
          state.selectedFromStation = st;
          renderTodaySearch();
          renderTomorrow();
          renderFullTimetable();
        });
      });
    }

    const btnSelectTo = document.getElementById('btn-select-to');
    if (btnSelectTo) {
      btnSelectTo.addEventListener('click', () => {
        window.openStationPicker('Select Destination Station', (st) => {
          state.selectedToStation = st;
          renderTodaySearch();
          renderTomorrow();
          renderFullTimetable();
        });
      });
    }

    let swapRotation = 0;
    const btnSwap = document.getElementById('btn-swap-stations');
    if (btnSwap) {
      btnSwap.addEventListener('click', () => {
        swapRotation += 180;
        btnSwap.style.transform = `rotate(${swapRotation}deg)`;
        const temp = state.selectedFromStation;
        state.selectedFromStation = state.selectedToStation;
        state.selectedToStation = temp;
        renderTodaySearch();
        renderTomorrow();
        renderFullTimetable();
      });
    }

    // Tomorrow swap
    const btnTmrwSwap = document.getElementById('btn-tmrw-swap');
    if (btnTmrwSwap) {
      btnTmrwSwap.addEventListener('click', () => {
        const temp = state.selectedFromStation;
        state.selectedFromStation = state.selectedToStation;
        state.selectedToStation = temp;
        renderTodaySearch();
        renderTomorrow();
        renderFullTimetable();
      });
    }

    // Full timetable swap
    const btnFullSwap = document.getElementById('btn-full-swap');
    if (btnFullSwap) {
      btnFullSwap.addEventListener('click', () => {
        const temp = state.selectedFromStation;
        state.selectedFromStation = state.selectedToStation;
        state.selectedToStation = temp;
        renderTodaySearch();
        renderTomorrow();
        renderFullTimetable();
      });
    }

    // Quick station pills on Today tab (From & To)
    attachQuickPills('quick-pills-from', (st) => {
      state.selectedFromStation = st;
      renderTodaySearch();
      renderTomorrow();
      renderFullTimetable();
    });

    attachQuickPills('quick-pills-to', (st) => {
      state.selectedToStation = st;
      renderTodaySearch();
      renderTomorrow();
      renderFullTimetable();
    });

    // Single station picker in Station Timings tab
    const btnSingle = document.getElementById('btn-select-single-station');
    if (btnSingle) {
      btnSingle.addEventListener('click', () => {
        window.openStationPicker('Select Station Timings', (st) => {
          state.selectedSingleStation = st;
          renderStationTimings();
        });
      });
    }

    attachQuickPills('quick-pills-station', (st) => {
      state.selectedSingleStation = st;
      renderStationTimings();
    });

    // Today search filter chips (Upcoming / All)
    const chipUp = document.getElementById('chip-upcoming');
    const chipAll = document.getElementById('chip-all-trains');
    if (chipUp && chipAll) {
      chipUp.addEventListener('click', () => {
        state.routeFilterMode = 'UPCOMING';
        chipUp.classList.add('active');
        chipAll.classList.remove('active');
        renderTodaySearch();
      });

      chipAll.addEventListener('click', () => {
        state.routeFilterMode = 'ALL';
        chipAll.classList.add('active');
        chipUp.classList.remove('active');
        renderTodaySearch();
      });
    }

    // Station Timings: Day Toggle (Today vs Tomorrow)
    const btnStToday = document.getElementById('btn-st-day-today');
    const btnStTmrw = document.getElementById('btn-st-day-tomorrow');
    const stFilterBar = document.getElementById('station-filters-bar');

    if (btnStToday && btnStTmrw) {
      btnStToday.addEventListener('click', () => {
        state.stationDay = 'TODAY';
        btnStToday.classList.add('active');
        btnStTmrw.classList.remove('active');
        if (stFilterBar) stFilterBar.classList.remove('hidden');
        renderStationTimings();
      });

      btnStTmrw.addEventListener('click', () => {
        state.stationDay = 'TOMORROW';
        btnStTmrw.classList.add('active');
        btnStToday.classList.remove('active');
        if (stFilterBar) stFilterBar.classList.add('hidden');
        renderStationTimings();
      });
    }

    // Station timings direction toggle
    const btnAluva = document.getElementById('btn-dir-aluva');
    const btnTpht = document.getElementById('btn-dir-tpht');

    if (btnAluva && btnTpht) {
      btnAluva.addEventListener('click', () => {
        state.stationDirection = 'DOWN'; // towards Aluva
        btnAluva.classList.add('active');
        btnTpht.classList.remove('active');
        renderStationTimings();
      });

      btnTpht.addEventListener('click', () => {
        state.stationDirection = 'UP'; // towards TPHT
        btnTpht.classList.add('active');
        btnAluva.classList.remove('active');
        renderStationTimings();
      });
    }

    // Station filter chips
    const chipStUp = document.getElementById('chip-st-upcoming');
    const chipStAll = document.getElementById('chip-st-all');
    if (chipStUp && chipStAll) {
      chipStUp.addEventListener('click', () => {
        state.stationFilterMode = 'UPCOMING';
        chipStUp.classList.add('active');
        chipStAll.classList.remove('active');
        renderStationTimings();
      });

      chipStAll.addEventListener('click', () => {
        state.stationFilterMode = 'ALL';
        chipStAll.classList.add('active');
        chipStUp.classList.remove('active');
        renderStationTimings();
      });
    }
  }

  function attachQuickPills(containerId, callback) {
    const container = document.getElementById(containerId);
    if (!container) return;
    container.querySelectorAll('.quick-pill').forEach((btn) => {
      btn.addEventListener('click', () => {
        const stId = parseInt(btn.getAttribute('data-station-id'), 10);
        const station = state.stations.find((s) => s.id === stId);
        if (station) {
          container.querySelectorAll('.quick-pill').forEach((b) => b.classList.remove('active'));
          btn.classList.add('active');
          callback(station);
        }
      });
    });
  }

  // ---------------------------------------------------------------------------
  // Train Stoppage Timeline Modal
  // ---------------------------------------------------------------------------
  function initTrainStopsModal() {
    const modal = document.getElementById('modal-train-stops');
    const closeBtn = document.getElementById('btn-close-stops');

    if (closeBtn && modal) closeBtn.addEventListener('click', () => modal.classList.add('hidden'));
    if (modal) {
      modal.addEventListener('click', (e) => {
        if (e.target === modal) modal.classList.add('hidden');
      });
    }

    window.openTrainStopsModal = async (trainNo, direction, terminalDepTime, highlightFromId, highlightToId, timetableName) => {
      const modalBadge = document.getElementById('modal-train-badge');
      const modalTitle = document.getElementById('modal-train-title');
      const modalSubtitle = document.getElementById('modal-train-subtitle');
      const startTimeElem = document.getElementById('modal-start-time');
      const endTimeElem = document.getElementById('modal-end-time');
      const countElem = document.getElementById('modal-stops-count');
      const durElem = document.getElementById('modal-total-duration');
      const timeline = document.getElementById('timeline-container');

      if (modalBadge) modalBadge.textContent = `TRAIN ${trainNo}`;
      if (modalTitle) modalTitle.textContent = `Train ${trainNo} Route Schedule`;
      const dirTitle = direction === 'UP' ? 'Aluva ➔ Tripunithura (UP Line)' : 'Tripunithura ➔ Aluva (DOWN Line)';
      if (modalSubtitle) modalSubtitle.textContent = `${dirTitle} • ${timetableName}`;

      const ttData = await getTimetableData(timetableName);
      if (!ttData || !ttData.trips) {
        showToast('No stoppage schedule available for this train.');
        return;
      }

      // Find trip
      const trip = ttData.trips.find(
        (t) => t.train_no === trainNo && t.direction === direction && t.terminal_departure === terminalDepTime
      ) || ttData.trips.find(
        (t) => t.train_no === trainNo && t.direction === direction
      );

      if (!trip || !trip.stops) {
        showToast('Stoppage schedule not found.');
        return;
      }

      // Ordered stops based on direction
      const sortedStations = [...state.stations].sort((a, b) => {
        return direction === 'UP' ? a.sequence - b.sequence : b.sequence - a.sequence;
      });

      const validStops = sortedStations.filter((st) => trip.stops[st.id]);
      if (validStops.length === 0) return;

      const firstStop = validStops[0];
      const lastStop = validStops[validStops.length - 1];
      const startDep = trip.stops[firstStop.id];
      const endArr = trip.stops[lastStop.id];

      if (startTimeElem) startTimeElem.textContent = startDep.slice(0, 5);
      if (endTimeElem) endTimeElem.textContent = endArr.slice(0, 5);
      if (countElem) countElem.textContent = `${validStops.length} Stops`;
      if (durElem) durElem.textContent = `${getDurationMinutes(startDep, endArr)}m`;

      if (!timeline) return;
      timeline.innerHTML = '';

      // Determine currently running station beacon if viewing today
      const nowStr = state.currentDate.toTimeString().split(' ')[0];
      const isRunningNow = isSameDay(state.selectedTravelDate, state.currentDate) && nowStr >= startDep && nowStr <= endArr;

      let hasMarkedActive = false;

      validStops.forEach((st, idx) => {
        const timeVal = trip.stops[st.id];
        const isOrigin = st.id === highlightFromId || idx === 0;
        const isDest = st.id === highlightToId || idx === validStops.length - 1;

        let activeStopClass = '';
        if (isRunningNow && !hasMarkedActive && timeVal >= nowStr) {
          activeStopClass = 'active-stop';
          hasMarkedActive = true;
        }

        const durFromStart = getDurationMinutes(startDep, timeVal);

        const item = document.createElement('div');
        item.className = `timeline-item ${isOrigin ? 'origin' : ''} ${isDest ? 'destination' : ''} ${activeStopClass}`;
        item.innerHTML = `
          <div class="timeline-bullet"></div>
          <div class="timeline-station-info">
            <span class="timeline-station-name">${st.name} (${st.code})</span>
            <span class="timeline-station-seq">Station #${st.sequence} ${isOrigin ? '• Boarding' : ''} ${isDest ? '• Destination' : ''}</span>
          </div>
          <div class="timeline-time-info">
            <span class="timeline-time-val">${timeVal.slice(0, 5)}</span>
            <span class="timeline-dur-val">+${durFromStart}m</span>
          </div>
        `;
        timeline.appendChild(item);
      });

      if (modal) modal.classList.remove('hidden');
    };
  }

  // ---------------------------------------------------------------------------
  // Releases & Info Modals
  // ---------------------------------------------------------------------------
  function initReleasesModal() {
    const modal = document.getElementById('modal-releases');
    const btnOpen = document.getElementById('btn-releases');
    const btnClose = document.getElementById('btn-close-releases');
    const btnFromInfo = document.getElementById('btn-open-releases-from-info');

    if (btnOpen && modal) btnOpen.addEventListener('click', () => modal.classList.remove('hidden'));
    if (btnClose && modal) btnClose.addEventListener('click', () => modal.classList.add('hidden'));
    if (btnFromInfo && modal) {
      btnFromInfo.addEventListener('click', () => {
        const infoM = document.getElementById('modal-info');
        if (infoM) infoM.classList.add('hidden');
        modal.classList.remove('hidden');
      });
    }

    if (modal) {
      modal.addEventListener('click', (e) => {
        if (e.target === modal) modal.classList.add('hidden');
      });
    }
  }

  function initInfoModal() {
    const modal = document.getElementById('modal-info');
    const btnOpen = document.getElementById('btn-info');
    const btnClose = document.getElementById('btn-close-info');

    if (btnOpen && modal) btnOpen.addEventListener('click', () => modal.classList.remove('hidden'));
    if (btnClose && modal) btnClose.addEventListener('click', () => modal.classList.add('hidden'));
    if (modal) {
      modal.addEventListener('click', (e) => {
        if (e.target === modal) modal.classList.add('hidden');
      });
    }
  }

  function initIosPrompt() {
    const isIos = /iPad|iPhone|iPod/.test(navigator.userAgent) && !window.MSStream;
    const isStandalone = window.navigator.standalone === true || window.matchMedia('(display-mode: standalone)').matches;
    const dismissed = localStorage.getItem('kmrl_dismiss_ios_prompt');

    if (isIos && !isStandalone && !dismissed) {
      const banner = document.getElementById('ios-prompt-banner');
      if (banner) {
        banner.classList.remove('hidden');
        const dismissBtn = document.getElementById('btn-dismiss-ios');
        if (dismissBtn) {
          dismissBtn.addEventListener('click', () => {
            banner.classList.add('hidden');
            localStorage.setItem('kmrl_dismiss_ios_prompt', 'true');
          });
        }
      }
    }
  }

  // Secret 5-Tap Gesture on KMRL Brand to unlock Admin mode
  function initLogoAdminGesture() {
    const brand = document.getElementById('brand-header');
    if (!brand) return;

    brand.addEventListener('click', () => {
      state.logoTapCount++;
      clearTimeout(state.logoTapTimeout);
      state.logoTapTimeout = setTimeout(() => {
        state.logoTapCount = 0;
      }, 3000);

      if (state.logoTapCount >= 5) {
        state.logoTapCount = 0;
        const adminNav = document.getElementById('nav-item-admin');
        if (adminNav) {
          adminNav.classList.remove('hidden');
          showToast('🔓 Admin Portal Unlocked! Tap Admin tab to open.');
        } else {
          window.location.href = 'admin.html';
        }
      }
    });

    const adminNav = document.getElementById('nav-item-admin');
    if (adminNav) {
      adminNav.addEventListener('click', () => {
        window.location.href = 'admin.html';
      });
    }
  }

  function showToast(msg) {
    const toast = document.getElementById('toast-notification');
    const msgElem = document.getElementById('toast-message');
    if (!toast || !msgElem) return;

    msgElem.textContent = msg;
    toast.classList.remove('hidden');
    setTimeout(() => {
      toast.classList.add('hidden');
    }, 3200);
  }

  // ---------------------------------------------------------------------------
  // Core Schedule Query Helpers
  // ---------------------------------------------------------------------------
  function getUpcomingJourneys(trips, fromId, toId) {
    const direction = fromId < toId ? 'UP' : 'DOWN';
    const journeys = [];

    for (const trip of trips) {
      if (trip.direction !== direction) continue;
      const fromDep = trip.stops[fromId];
      const toDep = trip.stops[toId];
      if (!fromDep || !toDep) continue;

      journeys.push({
        trainNo: trip.train_no,
        direction: trip.direction,
        departureTime: fromDep,
        arrivalTime: toDep,
        terminalDepartureTime: trip.terminal_departure,
        rawTrip: trip
      });
    }

    journeys.sort((a, b) => a.departureTime.localeCompare(b.departureTime));
    return journeys;
  }

  function getStationCalls(trips, stationId, direction) {
    const calls = [];
    for (const trip of trips) {
      if (trip.direction !== direction) continue;
      const dep = trip.stops[stationId];
      if (!dep) continue;

      calls.push({
        trainNo: trip.train_no,
        direction: trip.direction,
        departureTime: dep,
        terminalDepartureTime: trip.terminal_departure,
        rawTrip: trip
      });
    }
    calls.sort((a, b) => a.departureTime.localeCompare(b.departureTime));
    return calls;
  }

  function getCountdownSeconds(timeStr, targetDate = new Date()) {
    const parts = timeStr.split(':').map(Number);
    if (parts.length < 2) return -9999;
    const target = new Date(targetDate);
    target.setHours(parts[0], parts[1], parts[2] || 0, 0);
    return Math.floor((target.getTime() - state.currentDate.getTime()) / 1000);
  }

  function formatCountdown(sec) {
    if (sec <= 0 && sec >= -60) return 'DEPARTED';
    if (sec < -60) return 'DEPARTED';
    const mins = Math.floor(sec / 60);
    const s = sec % 60;
    if (mins >= 60) {
      const hrs = Math.floor(mins / 60);
      const remMins = mins % 60;
      return `${hrs}h ${remMins}m`;
    }
    if (mins > 0) return `${mins}m ${s}s`;
    return `${s}s`;
  }

  function isRevenue(terminalDep, stationDep, isSunday) {
    const morningStart = isSunday ? '07:30:00' : '06:00:00';
    const eveningEnd = '23:00:00';
    return stationDep >= morningStart && terminalDep <= eveningEnd;
  }

  function getDurationMinutes(dep, arr) {
    const [h1, m1, s1] = dep.split(':').map(Number);
    const [h2, m2, s2] = arr.split(':').map(Number);
    let diff = (h2 * 3600 + m2 * 60 + (s2 || 0)) - (h1 * 3600 + m1 * 60 + (s1 || 0));
    if (diff < 0) diff += 86400;
    return Math.round(diff / 60);
  }

  function isSameDay(d1, d2) {
    if (!d1 || !d2) return false;
    return d1.getFullYear() === d2.getFullYear() &&
           d1.getMonth() === d2.getMonth() &&
           d1.getDate() === d2.getDate();
  }

  // ---------------------------------------------------------------------------
  // Master Render Routines
  // ---------------------------------------------------------------------------
  function renderAll() {
    renderTodaySearch();
    renderStationTimings();
    renderTomorrow();
    renderFullTimetable();
  }

  // 1. TODAY ROUTE SEARCH
  async function renderTodaySearch() {
    const fromBtnName = document.getElementById('from-station-name');
    const toBtnName = document.getElementById('to-station-name');
    const emptyPrompt = document.getElementById('empty-prompt');
    const controls = document.getElementById('results-controls');
    const trainsList = document.getElementById('trains-list');
    const schedulePillName = document.getElementById('active-schedule-name');
    const routeScheduleTag = document.getElementById('route-schedule-name');
    const summaryCard = document.getElementById('journey-summary-card');
    const fareVal = document.getElementById('fare-val');
    const stationCountVal = document.getElementById('station-count-val');
    const durationVal = document.getElementById('duration-val');
    const firstTrainVal = document.getElementById('first-train-val');
    const lastTrainVal = document.getElementById('last-train-val');
    const travelTimeBadge = document.getElementById('travel-time-badge');

    if (!state.selectedFromStation || !state.selectedToStation) return;

    if (fromBtnName) fromBtnName.textContent = `${state.selectedFromStation.name} (${state.selectedFromStation.code})`;
    if (toBtnName) toBtnName.textContent = `${state.selectedToStation.name} (${state.selectedToStation.code})`;

    if (state.selectedFromStation.id === state.selectedToStation.id) {
      if (emptyPrompt) {
        emptyPrompt.classList.remove('hidden');
        const h3 = emptyPrompt.querySelector('h3');
        if (h3) h3.textContent = 'Source & Destination are the same';
        const p = emptyPrompt.querySelector('p');
        if (p) p.textContent = 'Please choose different stations to see trains.';
      }
      if (controls) controls.classList.add('hidden');
      if (summaryCard) summaryCard.classList.add('hidden');
      if (trainsList) trainsList.innerHTML = '';
      return;
    }

    if (emptyPrompt) emptyPrompt.classList.add('hidden');
    if (controls) controls.classList.remove('hidden');

    // Fare calculation
    const fareInfo = calculateFare(state.selectedFromStation, state.selectedToStation);
    if (summaryCard) {
      summaryCard.classList.remove('hidden');
      if (fareVal) fareVal.textContent = `₹${fareInfo.fare} Fare`;
      if (stationCountVal) stationCountVal.textContent = `${fareInfo.stations} Stops (${fareInfo.distance} km)`;
    }

    // Determine timetable for the selected travel date
    const ttName = getTimetableNameForDate(state.selectedTravelDate);
    state.activeTimetableName = ttName;
    if (schedulePillName) schedulePillName.textContent = `Active Schedule: ${ttName}`;
    if (routeScheduleTag) routeScheduleTag.textContent = ttName;

    const ttData = await getTimetableData(ttName);
    if (!ttData || !ttData.trips) {
      if (trainsList) trainsList.innerHTML = '<div class="empty-state"><p>No timetable data available.</p></div>';
      return;
    }

    const allJourneys = getUpcomingJourneys(ttData.trips, state.selectedFromStation.id, state.selectedToStation.id);
    if (allJourneys.length > 0) {
      const dur = getDurationMinutes(allJourneys[0].departureTime, allJourneys[0].arrivalTime);
      if (durationVal) durationVal.textContent = `~${dur} min`;
      if (firstTrainVal) firstTrainVal.textContent = allJourneys[0].departureTime.slice(0, 5);
      if (lastTrainVal) lastTrainVal.textContent = allJourneys[allJourneys.length - 1].departureTime.slice(0, 5);
      if (travelTimeBadge) travelTimeBadge.textContent = `Travel time: ${dur} min`;
    }

    const isToday = isSameDay(state.selectedTravelDate, state.currentDate);
    const isSunday = state.selectedTravelDate.getDay() === 0;

    // Filter upcoming only if user selected UPCOMING and viewing Today
    const filteredJourneys = (isToday && state.routeFilterMode === 'UPCOMING')
      ? allJourneys.filter((j) => getCountdownSeconds(j.departureTime, state.selectedTravelDate) > -60)
      : allJourneys;

    // Update filter chip labels with counts
    const upcomingCount = isToday ? allJourneys.filter((j) => getCountdownSeconds(j.departureTime, state.selectedTravelDate) > -60).length : allJourneys.length;
    const chipUp = document.getElementById('chip-upcoming');
    const chipAll = document.getElementById('chip-all-trains');
    if (chipUp) chipUp.textContent = `UPCOMING (${upcomingCount})`;
    if (chipAll) chipAll.textContent = `ALL TRAINS (${allJourneys.length})`;

    if (!trainsList) return;
    trainsList.innerHTML = '';

    if (filteredJourneys.length === 0) {
      trainsList.innerHTML = `
        <div class="empty-state">
          <h3>All trains have departed for today</h3>
          <p>All ${allJourneys.length} scheduled services have completed their run.</p>
          <button class="filter-chip active" style="margin-top: 14px;" id="btn-view-all-today">
            VIEW ALL TODAY'S TRAINS (${allJourneys.length})
          </button>
        </div>
      `;
      const btnViewAll = document.getElementById('btn-view-all-today');
      if (btnViewAll) {
        btnViewAll.addEventListener('click', () => {
          if (chipAll) chipAll.click();
        });
      }
      return;
    }

    // Render Hero Next Train
    const hero = filteredJourneys[0];
    const heroIsRev = isRevenue(hero.terminalDepartureTime, hero.departureTime, isSunday);
    const heroSec = isToday ? getCountdownSeconds(hero.departureTime, state.selectedTravelDate) : -9999;

    const heroCard = document.createElement('div');
    heroCard.className = `train-hero-card ${heroIsRev ? '' : 'non-revenue'}`;
    heroCard.title = 'Click to view complete stop-by-stop schedule';
    heroCard.innerHTML = `
      <div class="hero-header">
        <span class="badge-pill">${isToday && state.routeFilterMode === 'UPCOMING' ? 'NEXT TRAIN' : 'TRAIN ' + hero.trainNo}</span>
        ${heroIsRev ? '' : '<span class="badge-non-revenue">NON-REVENUE SERVICE</span>'}
      </div>
      <div class="hero-main">
        <div class="hero-time-box">
          <span class="hero-time">${hero.departureTime.slice(0, 5)}</span>
          <span class="hero-sub">Train ${hero.trainNo} • Departs ${state.selectedFromStation.name}</span>
        </div>
        <div class="hero-countdown">
          <span class="hero-countdown-label">${isToday ? (heroSec <= 0 ? 'STATUS' : 'DEPARTS IN') : 'SCHEDULED'}</span>
          <div class="hero-countdown-val" ${isToday ? `data-cd-time="${hero.departureTime}"` : ''}>
            ${isToday ? formatCountdown(heroSec) : `${getDurationMinutes(hero.departureTime, hero.arrivalTime)}m`}
          </div>
        </div>
      </div>
      <div class="hero-footer">
        <span>Arrives at ${state.selectedToStation.name}: <strong>${hero.arrivalTime.slice(0, 5)}</strong></span>
        <button class="btn-view-stops-mini">VIEW STOPS ➔</button>
      </div>
    `;

    heroCard.addEventListener('click', () => {
      window.openTrainStopsModal(
        hero.trainNo,
        hero.direction,
        hero.terminalDepartureTime,
        state.selectedFromStation.id,
        state.selectedToStation.id,
        ttName
      );
    });

    trainsList.appendChild(heroCard);

    // Following Trains List
    if (filteredJourneys.length > 1) {
      const followHeader = document.createElement('div');
      followHeader.className = 'following-label';
      followHeader.textContent = isToday && state.routeFilterMode === 'UPCOMING' ? 'FOLLOWING TRAINS' : 'ALL SCHEDULED SERVICES';
      trainsList.appendChild(followHeader);

      filteredJourneys.slice(1).forEach((item, idx) => {
        const isRev = isRevenue(item.terminalDepartureTime, item.departureTime, isSunday);
        const cdSec = isToday ? getCountdownSeconds(item.departureTime, state.selectedTravelDate) : -9999;
        const row = document.createElement('div');
        row.className = `train-row-card ${idx % 2 === 0 ? '' : 'alt-accent'} ${isRev ? '' : 'non-revenue'}`;
        row.title = 'Click to view complete stop-by-stop schedule';
        row.innerHTML = `
          <div class="row-left">
            <span class="row-train-no">Train ${item.trainNo} ${isRev ? '' : '• NON-REVENUE'}</span>
            <span class="row-time">${item.departureTime.slice(0, 5)}</span>
            <span class="row-dest">Arrives ${state.selectedToStation.name} at ${item.arrivalTime.slice(0, 5)}</span>
          </div>
          <div class="row-right">
            <span class="row-cd-label">${isToday ? (cdSec <= 0 ? 'STATUS' : 'IN') : 'DURATION'}</span>
            <div class="row-cd-val" ${isToday ? `data-cd-time="${item.departureTime}"` : ''}>
              ${isToday ? formatCountdown(cdSec) : `${getDurationMinutes(item.departureTime, item.arrivalTime)}m`}
            </div>
          </div>
        `;

        row.addEventListener('click', () => {
          window.openTrainStopsModal(
            item.trainNo,
            item.direction,
            item.terminalDepartureTime,
            state.selectedFromStation.id,
            state.selectedToStation.id,
            ttName
          );
        });

        trainsList.appendChild(row);
      });
    }
  }

  // 2. STATION TIMINGS TAB
  async function renderStationTimings() {
    const stBtnName = document.getElementById('single-station-name');
    const btnAluva = document.getElementById('btn-dir-aluva');
    const btnTpht = document.getElementById('btn-dir-tpht');
    const list = document.getElementById('station-trains-list');
    const firstTimeElem = document.getElementById('station-first-time');
    const lastTimeElem = document.getElementById('station-last-time');
    const totalCountElem = document.getElementById('station-total-count');

    if (!state.selectedSingleStation) return;
    const st = state.selectedSingleStation;
    if (stBtnName) stBtnName.textContent = `${st.name} (${st.code}) #${st.sequence}`;

    // Adjust button labels for Terminal Stations
    if (st.code === 'ALVA') {
      if (btnAluva) btnAluva.textContent = 'ARRIVALS (AT ALVA)';
      if (btnTpht) btnTpht.textContent = 'DEPARTURES (TO TPHT)';
    } else if (st.code === 'TPHT') {
      if (btnAluva) btnAluva.textContent = 'DEPARTURES (TO ALUVA)';
      if (btnTpht) btnTpht.textContent = 'ARRIVALS (AT TPHT)';
    } else {
      if (btnAluva) btnAluva.textContent = 'TOWARDS ALUVA';
      if (btnTpht) btnTpht.textContent = 'TOWARDS TPHT';
    }

    const isAluvaArrival = st.code === 'ALVA' && state.stationDirection === 'DOWN';
    const isTphtArrival = st.code === 'TPHT' && state.stationDirection === 'UP';
    const isArrival = isAluvaArrival || isTphtArrival;

    // Date resolution (Today vs Tomorrow)
    const isTomorrow = state.stationDay === 'TOMORROW';
    const targetDate = new Date();
    if (isTomorrow) targetDate.setDate(targetDate.getDate() + 1);

    const ttName = getTimetableNameForDate(targetDate);
    const ttData = await getTimetableData(ttName);
    if (!ttData || !ttData.trips) {
      if (list) list.innerHTML = '<div class="empty-state"><p>No timetable data for this station.</p></div>';
      return;
    }

    const allCalls = getStationCalls(ttData.trips, st.id, state.stationDirection);
    const isSunday = targetDate.getDay() === 0;

    // Stats
    if (allCalls.length > 0) {
      if (firstTimeElem) firstTimeElem.textContent = allCalls[0].departureTime.slice(0, 5);
      if (lastTimeElem) lastTimeElem.textContent = allCalls[allCalls.length - 1].departureTime.slice(0, 5);
      if (totalCountElem) totalCountElem.textContent = allCalls.length;
    } else {
      if (firstTimeElem) firstTimeElem.textContent = '--:--';
      if (lastTimeElem) lastTimeElem.textContent = '--:--';
      if (totalCountElem) totalCountElem.textContent = '0';
    }

    const filtered = (!isTomorrow && state.stationFilterMode === 'UPCOMING')
      ? allCalls.filter((c) => getCountdownSeconds(c.departureTime, targetDate) > -60)
      : allCalls;

    // Update filter counts
    const upcomingCount = !isTomorrow ? allCalls.filter((c) => getCountdownSeconds(c.departureTime, targetDate) > -60).length : allCalls.length;
    const chipStUp = document.getElementById('chip-st-upcoming');
    const chipStAll = document.getElementById('chip-st-all');
    if (chipStUp) chipStUp.textContent = `UPCOMING (${upcomingCount})`;
    if (chipStAll) chipStAll.textContent = `ALL TRAINS (${allCalls.length})`;

    if (!list) return;
    list.innerHTML = '';

    if (filtered.length === 0) {
      list.innerHTML = `
        <div class="empty-state">
          <h3>No ${isArrival ? 'upcoming arrivals' : 'upcoming departures'}</h3>
          <p>No more trains scheduled in this direction for ${isTomorrow ? 'tomorrow' : 'today'}.</p>
        </div>
      `;
      return;
    }

    // Hero First Train / Arrival Card
    const hero = filtered[0];
    const heroIsRev = isRevenue(hero.terminalDepartureTime, hero.departureTime, isSunday);
    const heroSec = !isTomorrow ? getCountdownSeconds(hero.departureTime, targetDate) : -9999;

    const heroCard = document.createElement('div');
    heroCard.className = `train-hero-card ${heroIsRev ? '' : 'non-revenue'}`;
    heroCard.title = 'Click to view complete stop-by-stop schedule';
    heroCard.innerHTML = `
      <div class="hero-header">
        <span class="badge-pill">${isArrival ? 'NEXT ARRIVAL' : (!isTomorrow && state.stationFilterMode === 'UPCOMING' ? 'NEXT DEPARTURE' : 'TRAIN ' + hero.trainNo)}</span>
        ${heroIsRev ? '' : '<span class="badge-non-revenue">NON-REVENUE SERVICE</span>'}
      </div>
      <div class="hero-main">
        <div class="hero-time-box">
          <span class="hero-time">${hero.departureTime.slice(0, 5)}</span>
          <span class="hero-sub">Train ${hero.trainNo} • ${isArrival ? 'Reaches ' + st.name : (state.stationDirection === 'UP' ? 'Towards Tripunithura' : 'Towards Aluva')}</span>
        </div>
        <div class="hero-countdown">
          <span class="hero-countdown-label">${!isTomorrow ? (heroSec <= 0 ? (isArrival ? 'ARRIVED' : 'DEPARTED') : (isArrival ? 'ARRIVES IN' : 'IN')) : 'SCHEDULED'}</span>
          <div class="hero-countdown-val" ${!isTomorrow ? `data-cd-time="${hero.departureTime}"` : ''}>
            ${!isTomorrow ? formatCountdown(heroSec) : hero.departureTime.slice(0, 5)}
          </div>
        </div>
      </div>
      <div class="hero-footer">
        <span>${isArrival ? 'Terminal Arrival' : 'Platform Departure'} • Timetable: ${ttName}</span>
        <button class="btn-view-stops-mini">VIEW STOPS ➔</button>
      </div>
    `;

    heroCard.addEventListener('click', () => {
      window.openTrainStopsModal(
        hero.trainNo,
        hero.direction,
        hero.terminalDepartureTime,
        st.id,
        state.stationDirection === 'UP' ? 25 : 1,
        ttName
      );
    });

    list.appendChild(heroCard);

    // Following rows
    if (filtered.length > 1) {
      const followHeader = document.createElement('div');
      followHeader.className = 'following-label';
      followHeader.textContent = isArrival ? 'FOLLOWING ARRIVALS' : 'FOLLOWING DEPARTURES';
      list.appendChild(followHeader);

      filtered.slice(1).forEach((item, idx) => {
        const isRev = isRevenue(item.terminalDepartureTime, item.departureTime, isSunday);
        const cdSec = !isTomorrow ? getCountdownSeconds(item.departureTime, targetDate) : -9999;
        const row = document.createElement('div');
        row.className = `train-row-card ${idx % 2 === 0 ? '' : 'alt-accent'} ${isRev ? '' : 'non-revenue'}`;
        row.title = 'Click to view complete stop-by-stop schedule';
        row.innerHTML = `
          <div class="row-left">
            <span class="row-train-no">Train ${item.trainNo} ${isRev ? '' : '• NON-REVENUE'}</span>
            <span class="row-time">${item.departureTime.slice(0, 5)}</span>
            <span class="row-dest">${isArrival ? 'Arrival at ' + st.name : (state.stationDirection === 'UP' ? 'Towards Tripunithura' : 'Towards Aluva')}</span>
          </div>
          <div class="row-right">
            <span class="row-cd-label">${!isTomorrow ? (cdSec <= 0 ? (isArrival ? 'ARRIVED' : 'DEPARTED') : (isArrival ? 'ARRIVES IN' : 'IN')) : 'SCHEDULED'}</span>
            <div class="row-cd-val" ${!isTomorrow ? `data-cd-time="${item.departureTime}"` : ''}>
              ${!isTomorrow ? formatCountdown(cdSec) : item.departureTime.slice(0, 5)}
            </div>
          </div>
        `;

        row.addEventListener('click', () => {
          window.openTrainStopsModal(
            item.trainNo,
            item.direction,
            item.terminalDepartureTime,
            st.id,
            state.stationDirection === 'UP' ? 25 : 1,
            ttName
          );
        });

        list.appendChild(row);
      });
    }
  }

  // 3. TOMORROW TAB
  async function renderTomorrow() {
    const list = document.getElementById('tomorrow-trains-list');
    const dateTitle = document.getElementById('tomorrow-date-str');
    const ttTitle = document.getElementById('tomorrow-tt-name');
    const tmrwFrom = document.getElementById('tmrw-from-name');
    const tmrwTo = document.getElementById('tmrw-to-name');
    const fareVal = document.getElementById('tmrw-fare-val');
    const durVal = document.getElementById('tmrw-dur-val');
    const countVal = document.getElementById('tmrw-count-val');

    if (!state.selectedFromStation || !state.selectedToStation) return;

    if (tmrwFrom) tmrwFrom.textContent = state.selectedFromStation.name;
    if (tmrwTo) tmrwTo.textContent = state.selectedToStation.name;

    const fareInfo = calculateFare(state.selectedFromStation, state.selectedToStation);
    if (fareVal) fareVal.textContent = `₹${fareInfo.fare} Fare (${fareInfo.stations} Stops)`;

    const tomorrow = new Date(state.currentDate);
    tomorrow.setDate(tomorrow.getDate() + 1);

    if (dateTitle) {
      dateTitle.textContent = tomorrow.toLocaleDateString('en-US', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    }

    const ttName = getTimetableNameForDate(tomorrow);
    if (ttTitle) ttTitle.textContent = `Schedule: ${ttName}`;

    const ttData = await getTimetableData(ttName);
    if (!ttData || !ttData.trips) {
      if (list) list.innerHTML = '<div class="empty-state"><p>No timetable data for tomorrow.</p></div>';
      return;
    }

    const journeys = getUpcomingJourneys(ttData.trips, state.selectedFromStation.id, state.selectedToStation.id);
    if (!list) return;
    list.innerHTML = '';

    if (journeys.length === 0) {
      list.innerHTML = '<div class="empty-state"><p>No trains scheduled for tomorrow.</p></div>';
      return;
    }

    const dur = getDurationMinutes(journeys[0].departureTime, journeys[0].arrivalTime);
    if (durVal) durVal.textContent = `~${dur} min`;
    if (countVal) countVal.textContent = `${journeys.length} Services`;

    const isSunday = tomorrow.getDay() === 0;
    journeys.forEach((item, idx) => {
      const isRev = isRevenue(item.terminalDepartureTime, item.departureTime, isSunday);
      const row = document.createElement('div');
      row.className = `train-row-card ${idx % 2 === 0 ? '' : 'alt-accent'} ${isRev ? '' : 'non-revenue'}`;
      row.title = 'Click to view complete stop-by-stop schedule';
      row.innerHTML = `
        <div class="row-left">
          <span class="row-train-no">Train ${item.trainNo} ${isRev ? '' : '• NON-REVENUE'}</span>
          <span class="row-time">${item.departureTime.slice(0, 5)}</span>
          <span class="row-dest">Arrives ${state.selectedToStation.name} at ${item.arrivalTime.slice(0, 5)}</span>
        </div>
        <div class="row-right">
          <span class="row-cd-label">DURATION</span>
          <div class="row-cd-val">${getDurationMinutes(item.departureTime, item.arrivalTime)}m</div>
        </div>
      `;

      row.addEventListener('click', () => {
        window.openTrainStopsModal(
          item.trainNo,
          item.direction,
          item.terminalDepartureTime,
          state.selectedFromStation.id,
          state.selectedToStation.id,
          ttName
        );
      });

      list.appendChild(row);
    });
  }

  // 4. FULL TIMETABLE TAB (Parity with APK FullTimetableScreen.kt)
  async function renderFullTimetable() {
    const list = document.getElementById('full-trains-list');
    const fromNameElem = document.getElementById('full-from-name');
    const toNameElem = document.getElementById('full-to-name');
    const durElem = document.getElementById('full-tt-duration');
    const totalElem = document.getElementById('full-tt-total');
    const scheduleElem = document.getElementById('full-tt-schedule');
    const dateDisplay = document.getElementById('full-tt-date-display');

    if (!state.selectedFromStation || !state.selectedToStation) return;

    if (fromNameElem) fromNameElem.textContent = state.selectedFromStation.name.toUpperCase();
    if (toNameElem) toNameElem.textContent = state.selectedToStation.name.toUpperCase();

    const dateOptions = { weekday: 'short', day: 'numeric', month: 'short' };
    if (dateDisplay) dateDisplay.textContent = state.selectedTravelDate.toLocaleDateString('en-US', dateOptions);

    const ttName = getTimetableNameForDate(state.selectedTravelDate);
    if (scheduleElem) scheduleElem.textContent = `Schedule: ${ttName}`;

    const ttData = await getTimetableData(ttName);
    if (!ttData || !ttData.trips) {
      if (list) list.innerHTML = '<div class="empty-state"><p>No timetable data available.</p></div>';
      return;
    }

    const allJourneys = getUpcomingJourneys(ttData.trips, state.selectedFromStation.id, state.selectedToStation.id);
    if (!list) return;
    list.innerHTML = '';

    if (allJourneys.length === 0) {
      list.innerHTML = '<div class="empty-state"><p>No scheduled services found between these stations.</p></div>';
      if (totalElem) totalElem.textContent = 'Total: 0';
      return;
    }

    const dur = getDurationMinutes(allJourneys[0].departureTime, allJourneys[0].arrivalTime);
    if (durElem) durElem.textContent = `Travel time: ${dur} min`;
    if (totalElem) totalElem.textContent = `Total Trains: ${allJourneys.length}`;

    const isSunday = state.selectedTravelDate.getDay() === 0;

    allJourneys.forEach((item, idx) => {
      const isRev = isRevenue(item.terminalDepartureTime, item.departureTime, isSunday);
      const row = document.createElement('div');
      row.className = `train-row-card ${idx % 2 === 0 ? '' : 'alt-accent'} ${isRev ? '' : 'non-revenue'}`;
      row.title = 'Click to view complete stop-by-stop schedule';
      row.innerHTML = `
        <div class="row-left">
          <span class="row-train-no">Train ${item.trainNo} ${isRev ? '' : '• NON-REVENUE'}</span>
          <span class="row-time">${item.departureTime.slice(0, 5)} ➔ ${item.arrivalTime.slice(0, 5)}</span>
          <span class="row-dest">Service #${idx + 1} of ${allJourneys.length}</span>
        </div>
        <div class="row-right">
          <span class="row-cd-label">DURATION</span>
          <div class="row-cd-val">${getDurationMinutes(item.departureTime, item.arrivalTime)}m</div>
        </div>
      `;

      row.addEventListener('click', () => {
        window.openTrainStopsModal(
          item.trainNo,
          item.direction,
          item.terminalDepartureTime,
          state.selectedFromStation.id,
          state.selectedToStation.id,
          ttName
        );
      });

      list.appendChild(row);
    });
  }

  // ---------------------------------------------------------------------------
  // Realtime Countdown Updater
  // ---------------------------------------------------------------------------
  function updateCountdowns() {
    const countdownElements = document.querySelectorAll('[data-cd-time]');
    countdownElements.forEach((el) => {
      const timeStr = el.getAttribute('data-cd-time');
      const sec = getCountdownSeconds(timeStr, state.selectedTravelDate);
      el.textContent = formatCountdown(sec);
    });
  }

})();
