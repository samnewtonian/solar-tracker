use solar_tracker::types::{Season, SolarPosition};
use solar_tracker::angles::*;

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

// ── DayOfYear ──

#[test]
fn test_day_of_year_known_dates() {
    assert_eq!(day_of_year(2026, 1, 1), 1);
    assert_eq!(day_of_year(2026, 3, 21), 80);
    assert_eq!(day_of_year(2026, 12, 31), 365);
}

#[test]
fn test_day_of_year_leap_year() {
    assert_eq!(day_of_year(2024, 2, 29), 60);
    assert_eq!(day_of_year(2024, 3, 1), 61);
    assert_eq!(day_of_year(2024, 12, 31), 366);
}

#[test]
fn test_day_of_year_century_leap_rules() {
    assert_eq!(day_of_year(2000, 2, 29), 60);
    assert_eq!(day_of_year(1900, 2, 28), 59);
}

#[test]
fn test_first_day_of_each_month_non_leap() {
    let expected = [1, 32, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335];
    for (i, &exp) in expected.iter().enumerate() {
        let month = i as i32 + 1;
        assert_eq!(day_of_year(2026, month, 1), exp, "Month {}", month);
    }
}

#[test]
fn test_first_day_of_each_month_leap() {
    let expected = [1, 32, 61, 92, 122, 153, 183, 214, 245, 275, 306, 336];
    for (i, &exp) in expected.iter().enumerate() {
        let month = i as i32 + 1;
        assert_eq!(day_of_year(2024, month, 1), exp, "Month {} (leap)", month);
    }
}

// ── NormalizeAngle ──

#[test]
fn test_normalize_angle_basic() {
    let cases: &[(f64, f64)] = &[
        (0.0, 0.0),
        (45.0, 45.0),
        (360.0, 0.0),
        (361.0, 1.0),
        (-1.0, 359.0),
        (-90.0, 270.0),
        (405.0, 45.0),
        (-180.0, 180.0),
    ];
    for &(input, expected) in cases {
        assert_approx!(normalize_angle(input), expected, 0.1);
    }
}

#[test]
fn test_normalize_angle_large() {
    let cases: &[(f64, f64)] = &[
        (720.0, 0.0),
        (810.0, 90.0),
        (-720.0, 0.0),
        (-450.0, 270.0),
    ];
    for &(input, expected) in cases {
        assert_approx!(normalize_angle(input), expected, 0.1);
    }
}

#[test]
fn test_normalize_angle_small_near_zero() {
    assert_approx!(normalize_angle(0.001), 0.001, 1e-6);
    assert_approx!(normalize_angle(-0.001), 359.999, 1e-6);
}

// ── SolarDeclination ──

#[test]
fn test_solar_declination_solstices_equinoxes() {
    assert_approx!(solar_declination(172), 23.45, 0.5);
    assert_approx!(solar_declination(355), -23.45, 0.5);
    assert_approx!(solar_declination(80), 0.0, 1.0);
    assert_approx!(solar_declination(264), 0.0, 1.0);
}

#[test]
fn test_solar_declination_bounded_all_days() {
    for n in 1..=365 {
        let decl = solar_declination(n);
        assert!(
            decl >= -23.45 && decl <= 23.45,
            "Day {}: {}",
            n, decl
        );
    }
}

// ── SolarPosition — Springfield Equinox ──

fn springfield_equinox() -> SolarPosition {
    solar_position(39.8, -89.6, 2026, 3, 21, 12, 0, -90.0)
}

#[test]
fn test_springfield_equinox_day_of_year() {
    assert_eq!(springfield_equinox().day_of_year, 80);
}

#[test]
fn test_springfield_equinox_declination() {
    assert_approx!(springfield_equinox().declination, 0.0, 1.0);
}

#[test]
fn test_springfield_equinox_eot() {
    assert_approx!(springfield_equinox().equation_of_time, -7.5, 2.0);
}

#[test]
fn test_springfield_equinox_zenith() {
    assert_approx!(springfield_equinox().zenith, 40.0, 2.0);
}

#[test]
fn test_springfield_equinox_altitude() {
    assert_approx!(springfield_equinox().altitude, 50.0, 2.0);
}

#[test]
fn test_springfield_equinox_azimuth() {
    let pos = springfield_equinox();
    assert!(
        pos.azimuth >= 174.0 && pos.azimuth <= 185.0,
        "azimuth={}",
        pos.azimuth
    );
}

// ── Summer / Winter solstice ──

#[test]
fn test_summer_solstice() {
    let pos = solar_position(39.8, -89.6, 2026, 6, 21, 12, 0, -90.0);
    assert_approx!(pos.declination, 23.45, 1.0);
    assert!(pos.zenith < 40.0, "zenith={}", pos.zenith);
    assert!(pos.altitude > 50.0, "altitude={}", pos.altitude);
}

#[test]
fn test_winter_solstice() {
    let pos = solar_position(39.8, -89.6, 2026, 12, 21, 12, 0, -90.0);
    assert_approx!(pos.declination, -23.45, 1.0);
    assert!(pos.zenith > 40.0, "zenith={}", pos.zenith);
    assert!(pos.altitude < 50.0, "altitude={}", pos.altitude);
}

// ── SingleAxisTilt ──

#[test]
fn test_single_axis_near_zero_at_noon() {
    let pos = solar_position(39.8, -89.6, 2026, 3, 21, 12, 0, -90.0);
    assert_approx!(single_axis_tilt(&pos, 39.8), 0.0, 5.0);
}

#[test]
fn test_single_axis_negative_morning() {
    let pos = solar_position(39.8, -89.6, 2026, 3, 21, 9, 0, -90.0);
    assert!(single_axis_tilt(&pos, 39.8) < 0.0);
}

#[test]
fn test_single_axis_positive_afternoon() {
    let pos = solar_position(39.8, -89.6, 2026, 3, 21, 15, 0, -90.0);
    assert!(single_axis_tilt(&pos, 39.8) > 0.0);
}

// ── DualAxisAngles ──

#[test]
fn test_dual_axis_tilt_equals_zenith() {
    let pos = solar_position(39.8, -89.6, 2026, 3, 21, 12, 0, -90.0);
    let da = dual_axis_angles(&pos);
    assert_approx!(da.tilt, pos.zenith, 0.01);
}

#[test]
fn test_dual_axis_panel_azimuth_opposite_sun() {
    let pos = solar_position(39.8, -89.6, 2026, 3, 21, 12, 0, -90.0);
    let da = dual_axis_angles(&pos);
    assert!(
        (354.0..=360.0).contains(&da.panel_azimuth)
            || (0.0..=5.0).contains(&da.panel_azimuth),
        "panel_azimuth={}",
        da.panel_azimuth
    );
}

// ── OptimalFixedTilt ──

#[test]
fn test_optimal_fixed_tilt_springfield() {
    assert_approx!(optimal_fixed_tilt(39.8), 33.3, 1.0);
}

#[test]
fn test_optimal_fixed_tilt_formula_values() {
    assert_approx!(optimal_fixed_tilt(40.0), 33.5, 0.1);
    assert_approx!(optimal_fixed_tilt(0.0), 3.1, 0.1);
    assert_approx!(optimal_fixed_tilt(-40.0), 33.5, 0.1);
}

#[test]
fn test_optimal_fixed_tilt_symmetric() {
    for lat in &[10.0, 25.0, 40.0, 55.0, 70.0, 85.0] {
        assert_approx!(optimal_fixed_tilt(*lat), optimal_fixed_tilt(-*lat), 1e-10);
    }
}

#[test]
fn test_optimal_fixed_tilt_increases_with_latitude() {
    let lats = [0.0, 15.0, 30.0, 45.0, 60.0, 75.0, 90.0];
    let tilts: Vec<f64> = lats.iter().map(|&l| optimal_fixed_tilt(l)).collect();
    for i in 0..tilts.len() - 1 {
        assert!(tilts[i] <= tilts[i + 1], "{:?}", tilts);
    }
}

// ── SeasonalTiltAdjustment ──

#[test]
fn test_seasonal_tilt_basic() {
    let lat = 40.0;
    assert_approx!(seasonal_tilt_adjustment(lat, Season::Summer), 25.0, 0.1);
    assert_approx!(seasonal_tilt_adjustment(lat, Season::Winter), 55.0, 0.1);
    assert_approx!(seasonal_tilt_adjustment(lat, Season::Spring), 40.0, 0.1);
    assert_approx!(seasonal_tilt_adjustment(lat, Season::Fall), 40.0, 0.1);
}

#[test]
fn test_seasonal_tilt_ordering() {
    let lat = 40.0;
    assert!(
        seasonal_tilt_adjustment(lat, Season::Summer)
            < seasonal_tilt_adjustment(lat, Season::Spring)
    );
    assert!(
        seasonal_tilt_adjustment(lat, Season::Spring)
            < seasonal_tilt_adjustment(lat, Season::Winter)
    );
}

#[test]
fn test_seasonal_tilt_equator() {
    assert_approx!(seasonal_tilt_adjustment(0.0, Season::Summer), -15.0, 0.01);
    assert_approx!(seasonal_tilt_adjustment(0.0, Season::Winter), 15.0, 0.01);
    assert_approx!(seasonal_tilt_adjustment(0.0, Season::Spring), 0.0, 0.01);
}

// ── ExampleCalculation ──

#[test]
fn test_example_calculation_runs() {
    let result = example_calculation();
    // Just verify all fields are populated
    assert!(result.solar_position.day_of_year > 0);
    assert!(result.fixed_optimal_tilt > 0.0);
}

// ── HourAngle ──

#[test]
fn test_hour_angle_solar_noon() {
    assert_approx!(hour_angle(12.0), 0.0, 0.01);
}

#[test]
fn test_hour_angle_morning_negative() {
    assert!(hour_angle(10.0) < 0.0);
}

#[test]
fn test_hour_angle_afternoon_positive() {
    assert!(hour_angle(14.0) > 0.0);
}

#[test]
fn test_hour_angle_known_values() {
    assert_approx!(hour_angle(13.0), 15.0, 0.01);
    assert_approx!(hour_angle(11.0), -15.0, 0.01);
    assert_approx!(hour_angle(15.0), 45.0, 0.01);
}

// ── DegRad roundtrip ──

#[test]
fn test_deg_rad_roundtrip() {
    for &deg in &[0.0, 45.0, 90.0, 180.0, 270.0, 360.0, -45.0, -180.0, 123.456] {
        assert_approx!(rad_to_deg(deg_to_rad(deg)), deg, 1e-10);
    }
}

#[test]
fn test_known_conversions() {
    assert_approx!(deg_to_rad(180.0), std::f64::consts::PI, 1e-10);
    assert_approx!(deg_to_rad(90.0), std::f64::consts::FRAC_PI_2, 1e-10);
    assert_approx!(deg_to_rad(0.0), 0.0, 1e-10);
    assert_approx!(rad_to_deg(std::f64::consts::PI), 180.0, 1e-10);
}

// ── Equator solar noon equinox ──

#[test]
fn test_equator_sun_overhead() {
    let pos = solar_position(0.0, 0.0, 2026, 3, 21, 12, 0, 0.0);
    assert_approx!(pos.declination, 0.0, 1.0);
    assert!(pos.zenith < 5.0, "zenith={}", pos.zenith);
    assert!(pos.altitude > 85.0, "altitude={}", pos.altitude);
}

// ── Polar latitude ──

#[test]
fn test_polar_summer() {
    let pos = solar_position(70.0, 15.0, 2026, 6, 21, 12, 0, 15.0);
    assert!(pos.altitude > 0.0);
    assert!(pos.zenith < 90.0);
}

#[test]
fn test_polar_winter() {
    let pos = solar_position(70.0, 15.0, 2026, 12, 21, 12, 0, 15.0);
    assert!(pos.zenith > 85.0);
}

// ── Southern hemisphere ──

#[test]
fn test_southern_hemisphere_reversed_seasons() {
    let pos_jun = solar_position(-33.9, 151.2, 2026, 6, 21, 12, 0, 150.0);
    let pos_dec = solar_position(-33.9, 151.2, 2026, 12, 21, 12, 0, 150.0);
    assert!(pos_jun.zenith > pos_dec.zenith);
    assert!(pos_jun.altitude < pos_dec.altitude);
}

// ── Midnight ──

#[test]
fn test_midnight_below_horizon() {
    let pos = solar_position(39.8, -89.6, 2026, 3, 21, 0, 0, -90.0);
    assert!(pos.altitude < 0.0);
    assert!(pos.zenith > 90.0);
}

// ── Zenith + altitude = 90 ──

#[test]
fn test_zenith_altitude_complement() {
    let cases: &[(f64, f64, i32, i32, i32, i32, i32, f64)] = &[
        (39.8, -89.6, 2026, 3, 21, 12, 0, -90.0),
        (0.0, 0.0, 2026, 6, 21, 12, 0, 0.0),
        (-33.9, 151.2, 2026, 12, 21, 15, 30, 150.0),
        (51.5, -0.1, 2026, 9, 22, 8, 0, 0.0),
        (70.0, 25.0, 2026, 6, 21, 18, 0, 30.0),
    ];
    for &(lat, lon, yr, mo, dy, hr, mn, std) in cases {
        let pos = solar_position(lat, lon, yr, mo, dy, hr, mn, std);
        assert_approx!(pos.zenith + pos.altitude, 90.0, 1e-10);
    }
}

// ── Azimuth always normalized ──

#[test]
fn test_azimuth_always_normalized() {
    let cases: &[(f64, f64, i32, i32, i32, i32, i32, f64)] = &[
        (39.8, -89.6, 2026, 1, 15, 8, 0, -90.0),
        (39.8, -89.6, 2026, 1, 15, 16, 0, -90.0),
        (39.8, -89.6, 2026, 7, 15, 6, 0, -90.0),
        (39.8, -89.6, 2026, 7, 15, 20, 0, -90.0),
        (-45.0, 170.0, 2026, 3, 21, 12, 0, 180.0),
        (60.0, 10.0, 2026, 6, 21, 3, 0, 15.0),
        (0.0, 0.0, 2026, 9, 22, 12, 0, 0.0),
    ];
    for &(lat, lon, yr, mo, dy, hr, mn, std) in cases {
        let pos = solar_position(lat, lon, yr, mo, dy, hr, mn, std);
        assert!(
            pos.azimuth >= 0.0 && pos.azimuth < 360.0,
            "azimuth={} for ({}, {}, {}-{}-{} {}:{})",
            pos.azimuth, lat, lon, yr, mo, dy, hr, mn
        );
    }
}

// ── Equation of time bounded ──

#[test]
fn test_equation_of_time_bounded() {
    for n in 1..=365 {
        let eot = equation_of_time(n);
        assert!(
            eot >= -15.0 && eot <= 17.0,
            "Day {}: {}",
            n, eot
        );
    }
}

// ── Zenith non-negative ──

#[test]
fn test_zenith_in_range() {
    let cases: &[(f64, f64, f64)] = &[
        (0.0, 0.0, 0.0),
        (45.0, 23.45, 0.0),
        (-45.0, -23.45, 0.0),
        (0.0, 23.45, 90.0),
        (89.0, 23.45, 0.0),
        (-89.0, -23.45, 0.0),
    ];
    for &(lat, decl, ha) in cases {
        let z = solar_zenith_angle(lat, decl, ha);
        assert!(z >= 0.0 && z <= 180.0, "zenith={}", z);
    }
}

// ── Dual axis panel azimuth normalized ──

#[test]
fn test_dual_axis_panel_azimuth_normalized() {
    for &solar_az in &[0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0, 359.9] {
        let pos = SolarPosition {
            day_of_year: 1,
            declination: 0.0,
            equation_of_time: 0.0,
            local_solar_time: 12.0,
            hour_angle: 0.0,
            zenith: 30.0,
            altitude: 60.0,
            azimuth: solar_az,
        };
        let da = dual_axis_angles(&pos);
        assert!(
            da.panel_azimuth >= 0.0 && da.panel_azimuth < 360.0,
            "panel_azimuth={} for solar_az={}",
            da.panel_azimuth, solar_az
        );
    }
}

// ── Multiple cities noon equinox ──

#[test]
fn test_multiple_cities_noon_equinox() {
    let cases: &[(&str, f64, f64, f64)] = &[
        ("London", 51.5, -0.1, 0.0),
        ("Tokyo", 35.7, 139.7, 135.0),
        ("Cape Town", -33.9, 18.4, 30.0),
        ("Quito", -0.2, -78.5, -75.0),
    ];
    for &(name, lat, lon, std) in cases {
        let pos = solar_position(lat, lon, 2026, 3, 21, 12, 0, std);
        assert_approx!(pos.zenith, lat.abs(), 8.0);
        let _ = name; // used in error messages via assert_approx
    }
}

// ── Morning/afternoon symmetry ──

#[test]
fn test_morning_afternoon_symmetry() {
    let pos_9am = solar_position(39.8, -89.6, 2026, 3, 21, 9, 0, -90.0);
    let pos_3pm = solar_position(39.8, -89.6, 2026, 3, 21, 15, 0, -90.0);
    assert_approx!(pos_9am.zenith, pos_3pm.zenith, 5.0);
    assert!(pos_9am.azimuth < 180.0);
    assert!(pos_3pm.azimuth > 180.0);
}
