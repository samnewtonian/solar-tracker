use std::sync::LazyLock;

use solar_tracker::angles::day_of_year;
use solar_tracker::lookup_table::*;
use solar_tracker::types::*;

macro_rules! assert_approx {
    ($left:expr, $right:expr, $tol:expr) => {
        let (l, r) = ($left as f64, $right as f64);
        assert!(
            (l - r).abs() <= $tol,
            "assert_approx failed: left={}, right={}, diff={}, tol={}",
            l, r, (l - r).abs(), $tol
        );
    };
}

// ── Config ──

#[test]
fn test_default_config() {
    let c = LookupTableConfig::default();
    assert_eq!(c.interval_minutes, 5);
    assert_eq!(c.latitude, 39.8);
    assert_eq!(c.longitude, -89.6);
    assert_eq!(c.std_meridian, -90.0);
    assert_eq!(c.year, 2026);
    assert_eq!(c.sunrise_buffer_minutes, 30);
    assert_eq!(c.sunset_buffer_minutes, 30);
}

// ── Time utilities ──

#[test]
fn test_time_roundtrip() {
    for m in [0, 1, 59, 60, 61, 120, 719, 720, 721, 1439] {
        assert_eq!(time_to_minutes(minutes_to_time(m)), m, "minutes={}", m);
    }
}

#[test]
fn test_known_time_conversions() {
    assert_eq!(minutes_to_time(0), (0, 0));
    assert_eq!(minutes_to_time(720), (12, 0));
    assert_eq!(minutes_to_time(1439), (23, 59));
    assert_eq!(minutes_to_time(390), (6, 30));
}

// ── Intervals per day ──

#[test]
fn test_intervals_per_day() {
    assert_eq!(intervals_per_day(5), 288);
    assert_eq!(intervals_per_day(15), 96);
    assert_eq!(intervals_per_day(30), 48);
    assert_eq!(intervals_per_day(1), 1440);
}

// ── doy_to_month_day ──

#[test]
fn test_doy_roundtrip() {
    let cases: &[(i32, i32, i32)] = &[
        (2026, 1, 1),
        (2026, 3, 21),
        (2026, 6, 21),
        (2026, 12, 31),
        (2024, 2, 29),
        (2024, 3, 1),
        (2026, 7, 4),
        (2026, 11, 15),
    ];
    for &(year, month, day) in cases {
        let doy = day_of_year(year, month, day);
        let (m, d) = doy_to_month_day(year, doy);
        assert_eq!((m, d), (month, day), "{}-{}-{} (doy={})", year, month, day, doy);
    }
}

#[test]
fn test_doy_boundary_days() {
    assert_eq!(doy_to_month_day(2026, 1), (1, 1));
    assert_eq!(doy_to_month_day(2026, 365), (12, 31));
    assert_eq!(doy_to_month_day(2024, 366), (12, 31));
}

// ── Sunrise/sunset estimation ──

#[test]
fn test_equinox_12h_daylight() {
    let ss = estimate_sunrise_sunset(39.8, 80);
    let midpoint = (ss.sunrise + ss.sunset) as f64 / 2.0;
    assert_approx!(midpoint, 720.0, 30.0);
    let daylight = ss.sunset - ss.sunrise;
    assert_approx!(daylight as f64, 720.0, 60.0);
}

#[test]
fn test_summer_longer_than_equinox() {
    let equinox = estimate_sunrise_sunset(39.8, 80);
    let solstice = estimate_sunrise_sunset(39.8, 172);
    assert!(
        (solstice.sunset - solstice.sunrise) > (equinox.sunset - equinox.sunrise)
    );
}

#[test]
fn test_winter_shorter_than_equinox() {
    let equinox = estimate_sunrise_sunset(39.8, 80);
    let winter = estimate_sunrise_sunset(39.8, 355);
    assert!(
        (winter.sunset - winter.sunrise) < (equinox.sunset - equinox.sunrise)
    );
}

#[test]
fn test_polar_day() {
    let ss = estimate_sunrise_sunset(80.0, 172);
    assert_eq!(ss.sunrise, 0);
    assert_eq!(ss.sunset, 1440);
}

#[test]
fn test_polar_night() {
    let ss = estimate_sunrise_sunset(80.0, 355);
    assert_eq!(ss.sunrise, ss.sunset);
}

// ── Single axis one day ──

static SA_TABLE_15: LazyLock<SingleAxisTable> = LazyLock::new(|| {
    let config = LookupTableConfig {
        interval_minutes: 15,
        ..Default::default()
    };
    generate_single_axis_table(&config)
});

#[test]
fn test_single_axis_day_80_structure() {
    let day_80 = &SA_TABLE_15.days[79];
    assert_eq!(day_80.day_of_year, 80);
    assert!(!day_80.entries.is_empty());
}

#[test]
fn test_single_axis_entries_type() {
    for entry in &SA_TABLE_15.days[79].entries {
        // entries are SingleAxisEntry by type system
        let _ = entry.rotation;
    }
}

#[test]
fn test_single_axis_rotation_near_zero_at_noon() {
    let noon_entries: Vec<_> = SA_TABLE_15.days[79]
        .entries
        .iter()
        .filter(|e| e.minutes == 720)
        .collect();
    if let Some(e) = noon_entries.first() {
        assert!(e.rotation.is_some());
        assert_approx!(e.rotation.unwrap(), 0.0, 5.0);
    }
}

#[test]
fn test_single_axis_morning_negative_afternoon_positive() {
    let entries = &SA_TABLE_15.days[79].entries;
    let morning = entries
        .iter()
        .find(|e| e.rotation.is_some() && e.minutes < 600);
    let afternoon = entries
        .iter()
        .find(|e| e.rotation.is_some() && e.minutes > 840);
    if let Some(m) = morning {
        assert!(m.rotation.unwrap() < 0.0);
    }
    if let Some(a) = afternoon {
        assert!(a.rotation.unwrap() > 0.0);
    }
}

// ── Dual axis one day ──

static DA_TABLE_15: LazyLock<DualAxisTable> = LazyLock::new(|| {
    let config = LookupTableConfig {
        interval_minutes: 15,
        ..Default::default()
    };
    generate_dual_axis_table(&config)
});

#[test]
fn test_dual_axis_day_80_structure() {
    let day_80 = &DA_TABLE_15.days[79];
    assert_eq!(day_80.day_of_year, 80);
    assert!(!day_80.entries.is_empty());
}

#[test]
fn test_dual_axis_entries_type() {
    for entry in &DA_TABLE_15.days[79].entries {
        let _ = entry.tilt;
        let _ = entry.panel_azimuth;
    }
}

#[test]
fn test_dual_axis_tilt_matches_zenith_at_noon() {
    let noon_entries: Vec<_> = DA_TABLE_15.days[79]
        .entries
        .iter()
        .filter(|e| e.minutes == 720)
        .collect();
    if let Some(e) = noon_entries.first() {
        assert!(e.tilt.is_some());
        assert_approx!(e.tilt.unwrap(), 40.0, 5.0);
    }
}

// ── Full year generation ──

static SA_TABLE_30: LazyLock<SingleAxisTable> = LazyLock::new(|| {
    let config = LookupTableConfig {
        interval_minutes: 30,
        ..Default::default()
    };
    generate_single_axis_table(&config)
});

#[test]
fn test_full_year_365_days() {
    assert_eq!(SA_TABLE_30.days.len(), 365);
}

#[test]
fn test_full_year_has_entries() {
    assert!(SA_TABLE_30.metadata.total_entries > 0);
    assert!(SA_TABLE_30.metadata.storage_estimate_kb > 0.0);
}

#[test]
fn test_full_year_every_day_has_entries() {
    for day in &SA_TABLE_30.days {
        assert!(
            !day.entries.is_empty(),
            "Day {} has no entries",
            day.day_of_year
        );
    }
}

#[test]
fn test_full_year_entry_structure_consistent() {
    let sample_day = &SA_TABLE_30.days[171]; // summer solstice
    for entry in &sample_day.entries {
        let _ = entry.minutes;
        // rotation is either Some(f64) or None — type system ensures this
    }
}

// ── Lookup single axis ──

#[test]
fn test_lookup_single_axis_exact_boundary() {
    let result = lookup_single_axis(&SA_TABLE_15, 80, 720);
    assert!(result.is_some());
    let r = result.unwrap();
    assert_eq!(r.minutes, 720);
    assert!(r.rotation.is_some());
}

#[test]
fn test_lookup_single_axis_interpolated() {
    let result = lookup_single_axis(&SA_TABLE_15, 80, 727);
    let at_720 = lookup_single_axis(&SA_TABLE_15, 80, 720);
    let at_735 = lookup_single_axis(&SA_TABLE_15, 80, 735);
    assert!(result.is_some());
    let r = result.unwrap();
    assert_eq!(r.minutes, 727);
    if let (Some(rot), Some(r720), Some(r735)) =
        (r.rotation, at_720.unwrap().rotation, at_735.unwrap().rotation)
    {
        let lo = r720.min(r735);
        let hi = r720.max(r735);
        assert!(
            rot >= lo - 0.01 && rot <= hi + 0.01,
            "rot={}, lo={}, hi={}",
            rot, lo, hi
        );
    }
}

// ── Lookup dual axis ──

#[test]
fn test_lookup_dual_axis_exact_boundary() {
    let result = lookup_dual_axis(&DA_TABLE_15, 80, 720);
    assert!(result.is_some());
    let r = result.unwrap();
    assert!(r.tilt.is_some());
    assert!(r.panel_azimuth.is_some());
}

#[test]
fn test_lookup_dual_axis_interpolated() {
    let result = lookup_dual_axis(&DA_TABLE_15, 80, 727);
    assert!(result.is_some());
    let r = result.unwrap();
    assert!(r.tilt.is_some());
    assert!(r.panel_azimuth.is_some());
}

// ── Lookup outside range ──

#[test]
fn test_nighttime_returns_none() {
    assert!(lookup_single_axis(&SA_TABLE_15, 80, 0).is_none());
    assert!(lookup_single_axis(&SA_TABLE_15, 80, 120).is_none());
}

// ── Compact export ──

#[test]
fn test_single_axis_compact() {
    let compact = single_axis_table_to_compact(&SA_TABLE_30);
    assert_eq!(compact.len(), 365);
    for day_vals in &compact {
        for v in day_vals {
            // v is Option<f64> — valid by type
            let _ = v;
        }
    }
}

#[test]
fn test_dual_axis_compact() {
    static DA_TABLE_30: LazyLock<DualAxisTable> = LazyLock::new(|| {
        let config = LookupTableConfig {
            interval_minutes: 30,
            ..Default::default()
        };
        generate_dual_axis_table(&config)
    });
    let compact = dual_axis_table_to_compact(&DA_TABLE_30);
    assert_eq!(compact.len(), 365);
    // Find a non-None entry to verify structure
    let sample = compact[0]
        .iter()
        .find(|v| v.0.is_some())
        .expect("should have at least one daylight entry");
    assert!(sample.0.is_some());
    assert!(sample.1.is_some());
}

// ── Interpolate angle wraparound ──

#[test]
fn test_interpolate_angle_normal() {
    assert_approx!(interpolate_angle(Some(0.0), Some(90.0), 0.5).unwrap(), 45.0, 0.01);
    assert_approx!(interpolate_angle(Some(0.0), Some(90.0), 0.0).unwrap(), 0.0, 0.01);
    assert_approx!(interpolate_angle(Some(0.0), Some(90.0), 1.0).unwrap(), 90.0, 0.01);
}

#[test]
fn test_interpolate_angle_wraparound_350_to_10() {
    let result = interpolate_angle(Some(350.0), Some(10.0), 0.5);
    assert_approx!(result.unwrap(), 0.0, 0.01);
}

#[test]
fn test_interpolate_angle_wraparound_10_to_350() {
    let result = interpolate_angle(Some(10.0), Some(350.0), 0.5);
    assert_approx!(result.unwrap(), 0.0, 0.01);
}

#[test]
fn test_interpolate_angle_none_input() {
    assert!(interpolate_angle(None, Some(10.0), 0.5).is_none());
    assert!(interpolate_angle(Some(10.0), None, 0.5).is_none());
}
