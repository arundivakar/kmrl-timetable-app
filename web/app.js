// KMRL Train Finder - Progressive Web App (PWA) Engine
(() => {
  'use strict';

  const FIREBASE_RTDB_BASE = 'https://kmrl-train-finder-default-rtdb.asia-southeast1.firebasedatabase.app';
  
  // State
  const state = {
    stations: [],
    timetablesMeta: {},
    dayDefaults: {},
    dateAssignments: {},
    loadedTimetables: {}, // cache of { timetableName: data }
    
    selectedFromStation: null,
    selectedToStation: null,
    routeFilterMode: 'UPCOMING', // 'UPCOMING' | 'ALL'

    selectedSingleStation: null,
    stationDirection: 'UP', // 'UP' = towards TPHT, 'DOWN' = towards Aluva
    stationFilterMode: 'UPCOMING', // 'UPCOMING' | 'ALL'

    activeTimetableName: '',
    currentDate: new Date(),
    isDarkMode: false
  };

  // ---------------------------------------------------------------------------
  // Initialization
  // ---------------------------------------------------------------------------
  document.addEventListener('DOMContentLoaded', async () => {
    initTheme();
    initClock();
    initNavigation();
    initStationPickerModal();
    initInfoModal();
    initIosPrompt();
    registerServiceWorker();

    try {
      await loadInitialData();
      await syncFirebaseDefaults();
      setDefaultStations();
      renderAll();

      // Auto-sync with Firebase when app resumes or regains focus (e.g. unlocking iPhone)
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') {
          syncFirebaseDefaults().then(() => renderAll());
        }
      });
      window.addEventListener('focus', () => {
        syncFirebaseDefaults().then(() => renderAll());
      });

      // Background sync every 2 minutes so active users get live admin updates without manual reload
      setInterval(() => {
        syncFirebaseDefaults().then(() => renderAll());
      }, 120000);
    } catch (err) {
      console.error('Initialization error:', err);
    }
  });

  // ---------------------------------------------------------------------------
  // Service Worker Registration
  // ---------------------------------------------------------------------------
  function registerServiceWorker() {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('sw.js')
        .then((reg) => {
          console.log('KMRL Service Worker registered:', reg.scope);
          const badge = document.getElementById('offline-status-badge');
          if (badge) badge.textContent = '✅ Active (Cache Ready)';
        })
        .catch((err) => {
          console.warn('Service Worker registration failed:', err);
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
      // Sunday default
      return state.dayDefaults[6] || '13S010326_5TPHT_MRP1';
    } else {
      // In KMRL, Mon-Sat (0..5) uses Weekday default (day 0)
      return state.dayDefaults[0] || state.dayDefaults[pythonDay] || '16W070926_TPHTOFFPEAK_MRP1';
    }
  }

  async function getTimetableData(timetableName) {
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
      state.selectedFromStation = state.stations[0]; // Aluva
      state.selectedToStation = state.stations[state.stations.length - 1]; // Tripunithura
      state.selectedSingleStation = state.stations[0]; // Aluva
    }
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
  // Station Picker Modal
  // ---------------------------------------------------------------------------
  let pickerCallback = null;

  function initStationPickerModal() {
    const modal = document.getElementById('modal-station-picker');
    const closeBtn = document.getElementById('btn-close-modal');
    const searchInput = document.getElementById('station-search-input');
    const listContainer = document.getElementById('station-list-items');

    closeBtn.addEventListener('click', () => modal.classList.add('hidden'));
    modal.addEventListener('click', (e) => {
      if (e.target === modal) modal.classList.add('hidden');
    });

    searchInput.addEventListener('input', () => {
      const q = searchInput.value.toLowerCase().trim();
      renderStationModalList(q);
    });

    function renderStationModalList(filter = '') {
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
            modal.classList.add('hidden');
          });
          listContainer.appendChild(item);
        });
    }

    window.openStationPicker = (title, onSelect) => {
      document.getElementById('modal-station-title').textContent = title;
      searchInput.value = '';
      pickerCallback = onSelect;
      renderStationModalList();
      modal.classList.remove('hidden');
      setTimeout(() => searchInput.focus(), 150);
    };

    // Attach to UI buttons
    document.getElementById('btn-select-from').addEventListener('click', () => {
      window.openStationPicker('Select Source Station', (st) => {
        state.selectedFromStation = st;
        renderTodaySearch();
      });
    });

    document.getElementById('btn-select-to').addEventListener('click', () => {
      window.openStationPicker('Select Destination Station', (st) => {
        state.selectedToStation = st;
        renderTodaySearch();
      });
    });

    document.getElementById('btn-swap-stations').addEventListener('click', () => {
      const temp = state.selectedFromStation;
      state.selectedFromStation = state.selectedToStation;
      state.selectedToStation = temp;
      renderTodaySearch();
    });

    document.getElementById('btn-select-single-station').addEventListener('click', () => {
      window.openStationPicker('Select Station', (st) => {
        state.selectedSingleStation = st;
        renderStationTimings();
      });
    });

    // Today search filter chips
    document.getElementById('chip-upcoming').addEventListener('click', () => {
      state.routeFilterMode = 'UPCOMING';
      document.getElementById('chip-upcoming').classList.add('active');
      document.getElementById('chip-all-trains').classList.remove('active');
      renderTodaySearch();
    });

    document.getElementById('chip-all-trains').addEventListener('click', () => {
      state.routeFilterMode = 'ALL';
      document.getElementById('chip-all-trains').classList.add('active');
      document.getElementById('chip-upcoming').classList.remove('active');
      renderTodaySearch();
    });

    // Station timings direction toggle
    const btnAluva = document.getElementById('btn-dir-aluva');
    const btnTpht = document.getElementById('btn-dir-tpht');

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

    // Station filter chips
    document.getElementById('chip-st-upcoming').addEventListener('click', () => {
      state.stationFilterMode = 'UPCOMING';
      document.getElementById('chip-st-upcoming').classList.add('active');
      document.getElementById('chip-st-all').classList.remove('active');
      renderStationTimings();
    });

    document.getElementById('chip-st-all').addEventListener('click', () => {
      state.stationFilterMode = 'ALL';
      document.getElementById('chip-st-all').classList.add('active');
      document.getElementById('chip-st-upcoming').classList.remove('active');
      renderStationTimings();
    });
  }

  // ---------------------------------------------------------------------------
  // Info & iOS Prompt
  // ---------------------------------------------------------------------------
  function initInfoModal() {
    const modal = document.getElementById('modal-info');
    const btnOpen = document.getElementById('btn-info');
    const btnClose = document.getElementById('btn-close-info');

    btnOpen.addEventListener('click', () => modal.classList.remove('hidden'));
    btnClose.addEventListener('click', () => modal.classList.add('hidden'));
    modal.addEventListener('click', (e) => {
      if (e.target === modal) modal.classList.add('hidden');
    });
  }

  function initIosPrompt() {
    const isIos = /iPad|iPhone|iPod/.test(navigator.userAgent) && !window.MSStream;
    const isStandalone = window.navigator.standalone === true || window.matchMedia('(display-mode: standalone)').matches;
    const dismissed = localStorage.getItem('kmrl_dismiss_ios_prompt');

    if (isIos && !isStandalone && !dismissed) {
      const banner = document.getElementById('ios-prompt-banner');
      if (banner) {
        banner.classList.remove('hidden');
        document.getElementById('btn-dismiss-ios').addEventListener('click', () => {
          banner.classList.add('hidden');
          localStorage.setItem('kmrl_dismiss_ios_prompt', 'true');
        });
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Core Schedule Query Helpers
  // ---------------------------------------------------------------------------
  function getUpcomingJourneys(trips, fromId, toId, timeStr) {
    const direction = fromId < toId ? 'UP' : 'DOWN';
    const journeys = [];

    for (const trip of trips) {
      if (trip.direction !== direction) continue;
      const fromDep = trip.stops[fromId];
      const toDep = trip.stops[toId];
      if (!fromDep || !toDep) continue;

      journeys.push({
        trainNo: trip.train_no,
        departureTime: fromDep,
        arrivalTime: toDep,
        terminalDepartureTime: trip.terminal_departure
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
        terminalDepartureTime: trip.terminal_departure
      });
    }
    calls.sort((a, b) => a.departureTime.localeCompare(b.departureTime));
    return calls;
  }

  function getCountdownSeconds(timeStr, now = new Date()) {
    const parts = timeStr.split(':').map(Number);
    if (parts.length < 2) return -9999;
    const target = new Date(now);
    target.setHours(parts[0], parts[1], parts[2] || 0, 0);
    return Math.floor((target.getTime() - now.getTime()) / 1000);
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

  // ---------------------------------------------------------------------------
  // Rendering
  // ---------------------------------------------------------------------------
  function renderAll() {
    renderTodaySearch();
    renderStationTimings();
    renderTomorrow();
  }

  async function renderTodaySearch() {
    const fromBtnName = document.getElementById('from-station-name');
    const toBtnName = document.getElementById('to-station-name');
    const emptyPrompt = document.getElementById('empty-prompt');
    const controls = document.getElementById('results-controls');
    const trainsList = document.getElementById('trains-list');
    const travelTimeBadge = document.getElementById('travel-time-badge');
    const schedulePillName = document.getElementById('active-schedule-name');

    if (!state.selectedFromStation || !state.selectedToStation) return;

    fromBtnName.textContent = `${state.selectedFromStation.name} (${state.selectedFromStation.code})`;
    toBtnName.textContent = `${state.selectedToStation.name} (${state.selectedToStation.code})`;

    if (state.selectedFromStation.id === state.selectedToStation.id) {
      emptyPrompt.classList.remove('hidden');
      emptyPrompt.querySelector('h3').textContent = 'Source & Destination are the same';
      emptyPrompt.querySelector('p').textContent = 'Please choose different stations to see trains.';
      controls.classList.add('hidden');
      trainsList.innerHTML = '';
      return;
    }

    emptyPrompt.classList.add('hidden');
    controls.classList.remove('hidden');

    // Get today's timetable
    const ttName = getTimetableNameForDate(state.currentDate);
    state.activeTimetableName = ttName;
    schedulePillName.textContent = `Schedule: ${ttName}`;

    const ttData = await getTimetableData(ttName);
    if (!ttData || !ttData.trips) {
      trainsList.innerHTML = '<div class="empty-state"><p>No timetable data available.</p></div>';
      return;
    }

    const allJourneys = getUpcomingJourneys(ttData.trips, state.selectedFromStation.id, state.selectedToStation.id, '00:00:00');
    if (allJourneys.length > 0) {
      const dur = getDurationMinutes(allJourneys[0].departureTime, allJourneys[0].arrivalTime);
      travelTimeBadge.textContent = `Travel time: ${dur} min`;
    }

    const now = state.currentDate;
    const isSunday = now.getDay() === 0;

    // Filter by upcoming if in UPCOMING mode
    const filteredJourneys = state.routeFilterMode === 'UPCOMING'
      ? allJourneys.filter((j) => getCountdownSeconds(j.departureTime, now) > -60)
      : allJourneys;

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
          document.getElementById('chip-all-trains').click();
        });
      }
      return;
    }

    // Render Hero Next Train
    const hero = filteredJourneys[0];
    const heroIsRev = isRevenue(hero.terminalDepartureTime, hero.departureTime, isSunday);
    const heroSec = getCountdownSeconds(hero.departureTime, now);

    const heroCard = document.createElement('div');
    heroCard.className = `train-hero-card ${heroIsRev ? '' : 'non-revenue'}`;
    heroCard.innerHTML = `
      <div class="hero-header">
        <span class="badge-pill">${state.routeFilterMode === 'ALL' ? 'TRAIN ' + hero.trainNo : 'NEXT TRAIN'}</span>
        ${heroIsRev ? '' : '<span class="badge-non-revenue">NON-REVENUE SERVICE</span>'}
      </div>
      <div class="hero-main">
        <div class="hero-time-box">
          <span class="hero-time">${hero.departureTime}</span>
          <span class="hero-sub">Train ${hero.trainNo} • Departs ${state.selectedFromStation.name}</span>
        </div>
        <div class="hero-countdown">
          <span class="hero-countdown-label">${heroSec <= 0 ? 'STATUS' : 'DEPARTS IN'}</span>
          <div class="hero-countdown-val" data-cd-time="${hero.departureTime}">${formatCountdown(heroSec)}</div>
        </div>
      </div>
      <div class="hero-footer">
        <span>Arrives at ${state.selectedToStation.name}: <strong>${hero.arrivalTime}</strong></span>
        <span>${getDurationMinutes(hero.departureTime, hero.arrivalTime)} min</span>
      </div>
    `;
    trainsList.appendChild(heroCard);

    // Following Trains List
    if (filteredJourneys.length > 1) {
      const followHeader = document.createElement('div');
      followHeader.className = 'following-label';
      followHeader.textContent = 'FOLLOWING TRAINS';
      trainsList.appendChild(followHeader);

      filteredJourneys.slice(1).forEach((item, idx) => {
        const isRev = isRevenue(item.terminalDepartureTime, item.departureTime, isSunday);
        const cdSec = getCountdownSeconds(item.departureTime, now);
        const row = document.createElement('div');
        row.className = `train-row-card ${idx % 2 === 0 ? '' : 'alt-accent'} ${isRev ? '' : 'non-revenue'}`;
        row.innerHTML = `
          <div class="row-left">
            <span class="row-train-no">Train ${item.trainNo} ${isRev ? '' : '• NON-REVENUE'}</span>
            <span class="row-time">${item.departureTime}</span>
            <span class="row-dest">Arrives ${state.selectedToStation.name} at ${item.arrivalTime}</span>
          </div>
          <div class="row-right">
            <span class="row-cd-label">${cdSec <= 0 ? 'STATUS' : 'IN'}</span>
            <div class="row-cd-val" data-cd-time="${item.departureTime}">${formatCountdown(cdSec)}</div>
          </div>
        `;
        trainsList.appendChild(row);
      });
    }
  }

  async function renderStationTimings() {
    const stBtnName = document.getElementById('single-station-name');
    const btnAluva = document.getElementById('btn-dir-aluva');
    const btnTpht = document.getElementById('btn-dir-tpht');
    const list = document.getElementById('station-trains-list');

    if (!state.selectedSingleStation) return;
    const st = state.selectedSingleStation;
    stBtnName.textContent = `${st.name} (${st.code})`;

    // Adjust button labels for Terminal Stations
    if (st.code === 'ALVA') {
      btnAluva.textContent = 'ARRIVALS (AT ALVA)';
      btnTpht.textContent = 'DEPARTURES (TO TPHT)';
    } else if (st.code === 'TPHT') {
      btnAluva.textContent = 'DEPARTURES (TO ALUVA)';
      btnTpht.textContent = 'ARRIVALS (AT TPHT)';
    } else {
      btnAluva.textContent = '→ ALUVA';
      btnTpht.textContent = '→ TPHT';
    }

    const isAluvaArrival = st.code === 'ALVA' && state.stationDirection === 'DOWN';
    const isTphtArrival = st.code === 'TPHT' && state.stationDirection === 'UP';
    const isArrival = isAluvaArrival || isTphtArrival;

    const ttName = getTimetableNameForDate(state.currentDate);
    const ttData = await getTimetableData(ttName);
    if (!ttData || !ttData.trips) {
      list.innerHTML = '<div class="empty-state"><p>No timetable data.</p></div>';
      return;
    }

    const allCalls = getStationCalls(ttData.trips, st.id, state.stationDirection);
    const now = state.currentDate;
    const isSunday = now.getDay() === 0;

    const filtered = state.stationFilterMode === 'UPCOMING'
      ? allCalls.filter((c) => getCountdownSeconds(c.departureTime, now) > -60)
      : allCalls;

    list.innerHTML = '';

    if (filtered.length === 0) {
      list.innerHTML = `
        <div class="empty-state">
          <h3>No ${isArrival ? 'upcoming arrivals' : 'upcoming trains'}</h3>
          <p>No more trains scheduled in this direction for today.</p>
        </div>
      `;
      return;
    }

    // Hero First Train / Arrival Card
    const hero = filtered[0];
    const heroIsRev = isRevenue(hero.terminalDepartureTime, hero.departureTime, isSunday);
    const heroSec = getCountdownSeconds(hero.departureTime, now);

    const heroCard = document.createElement('div');
    heroCard.className = `train-hero-card ${heroIsRev ? '' : 'non-revenue'}`;
    heroCard.innerHTML = `
      <div class="hero-header">
        <span class="badge-pill">${isArrival ? 'NEXT ARRIVAL' : 'NEXT TRAIN'}</span>
        ${heroIsRev ? '' : '<span class="badge-non-revenue">NON-REVENUE SERVICE</span>'}
      </div>
      <div class="hero-main">
        <div class="hero-time-box">
          <span class="hero-time">${hero.departureTime}</span>
          <span class="hero-sub">Train ${hero.trainNo} • ${isArrival ? 'Reaches ' + st.name : (state.stationDirection === 'UP' ? 'Towards TPHT' : 'Towards Aluva')}</span>
        </div>
        <div class="hero-countdown">
          <span class="hero-countdown-label">${heroSec <= 0 ? (isArrival ? 'ARRIVED' : 'DEPARTED') : (isArrival ? 'ARRIVES IN' : 'IN')}</span>
          <div class="hero-countdown-val" data-cd-time="${hero.departureTime}">${formatCountdown(heroSec)}</div>
        </div>
      </div>
    `;
    list.appendChild(heroCard);

    // Following rows
    if (filtered.length > 1) {
      const followHeader = document.createElement('div');
      followHeader.className = 'following-label';
      followHeader.textContent = isArrival ? 'FOLLOWING ARRIVALS' : 'FOLLOWING TRAINS';
      list.appendChild(followHeader);

      filtered.slice(1).forEach((item, idx) => {
        const isRev = isRevenue(item.terminalDepartureTime, item.departureTime, isSunday);
        const cdSec = getCountdownSeconds(item.departureTime, now);
        const row = document.createElement('div');
        row.className = `train-row-card ${idx % 2 === 0 ? '' : 'alt-accent'} ${isRev ? '' : 'non-revenue'}`;
        row.innerHTML = `
          <div class="row-left">
            <span class="row-train-no">Train ${item.trainNo} ${isRev ? '' : '• NON-REVENUE'}</span>
            <span class="row-time">${item.departureTime}</span>
            <span class="row-dest">${isArrival ? 'Arrival at ' + st.name : (state.stationDirection === 'UP' ? 'Towards TPHT' : 'Towards Aluva')}</span>
          </div>
          <div class="row-right">
            <span class="row-cd-label">${cdSec <= 0 ? (isArrival ? 'ARRIVED' : 'DEPARTED') : (isArrival ? 'ARRIVES IN' : 'IN')}</span>
            <div class="row-cd-val" data-cd-time="${item.departureTime}">${formatCountdown(cdSec)}</div>
          </div>
        `;
        list.appendChild(row);
      });
    }
  }

  async function renderTomorrow() {
    const list = document.getElementById('tomorrow-trains-list');
    const dateTitle = document.getElementById('tomorrow-date-str');
    if (!state.selectedFromStation || !state.selectedToStation) return;

    const tomorrow = new Date(state.currentDate);
    tomorrow.setDate(tomorrow.getDate() + 1);

    if (dateTitle) {
      dateTitle.textContent = tomorrow.toLocaleDateString('en-US', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    }

    const ttName = getTimetableNameForDate(tomorrow);
    const ttData = await getTimetableData(ttName);
    if (!ttData || !ttData.trips) {
      list.innerHTML = '<div class="empty-state"><p>No timetable data for tomorrow.</p></div>';
      return;
    }

    const journeys = getUpcomingJourneys(ttData.trips, state.selectedFromStation.id, state.selectedToStation.id, '00:00:00');
    list.innerHTML = '';

    if (journeys.length === 0) {
      list.innerHTML = '<div class="empty-state"><p>No trains scheduled for tomorrow.</p></div>';
      return;
    }

    const isSunday = tomorrow.getDay() === 0;
    journeys.forEach((item, idx) => {
      const isRev = isRevenue(item.terminalDepartureTime, item.departureTime, isSunday);
      const row = document.createElement('div');
      row.className = `train-row-card ${idx % 2 === 0 ? '' : 'alt-accent'} ${isRev ? '' : 'non-revenue'}`;
      row.innerHTML = `
        <div class="row-left">
          <span class="row-train-no">Train ${item.trainNo} ${isRev ? '' : '• NON-REVENUE'}</span>
          <span class="row-time">${item.departureTime}</span>
          <span class="row-dest">Arrives ${state.selectedToStation.name} at ${item.arrivalTime}</span>
        </div>
        <div class="row-right">
          <span class="row-cd-label">SCHEDULED</span>
          <div class="row-cd-val">${getDurationMinutes(item.departureTime, item.arrivalTime)}m</div>
        </div>
      `;
      list.appendChild(row);
    });
  }

  function updateCountdowns() {
    const countdownElements = document.querySelectorAll('[data-cd-time]');
    countdownElements.forEach((el) => {
      const timeStr = el.getAttribute('data-cd-time');
      const sec = getCountdownSeconds(timeStr, state.currentDate);
      el.textContent = formatCountdown(sec);
    });
  }

})();
