use chrono::{DateTime, Datelike, TimeZone, Timelike, Utc};

use crate::types::{DualAxisAngles, ExampleResult, Season, SolarPosition};

pub const EARTH_AXIAL_TILT: f64 = 23.45;
pub const DEGREES_PER_HOUR: f64 = 15.0;

pub fn deg_to_rad(deg: f64) -> f64 {
    deg * (std::f64::consts::PI / 180.0)
}

pub fn rad_to_deg(rad: f64) -> f64 {
    rad * (180.0 / std::f64::consts::PI)
}

pub fn normalize_angle(angle: f64) -> f64 {
    angle.rem_euclid(360.0)
}

pub fn leap_year(year: i32) -> bool {
    (year % 400 == 0) || (year % 4 == 0 && year % 100 != 0)
}

pub fn days_in_months(year: i32) -> [u32; 12] {
    [
        31,
        if leap_year(year) { 29 } else { 28 },
        31, 30, 31, 30, 31, 31, 30, 31, 30, 31,
    ]
}

pub fn day_of_year(year: i32, month: u32, day: u32) -> i32 {
    let dim = days_in_months(year);
    let sum: u32 = dim[..(month - 1) as usize].iter().sum();
    (sum + day) as i32
}

pub fn intermediate_angle_b(n: i32) -> f64 {
    deg_to_rad((n - 1) as f64 * (360.0 / 365.0))
}

pub fn equation_of_time(n: i32) -> f64 {
    let b = intermediate_angle_b(n);
    229.18
        * (0.000075
            + 0.001868 * b.cos()
            - 0.032077 * b.sin()
            - 0.014615 * (2.0 * b).cos()
            - 0.040849 * (2.0 * b).sin())
}

pub fn utc_lst_correction(longitude: f64, eot: f64) -> f64 {
    (4.0 * longitude + eot) / 60.0
}

pub fn hour_angle(local_solar_time: f64) -> f64 {
    DEGREES_PER_HOUR * (local_solar_time - 12.0)
}

pub fn solar_declination(n: i32) -> f64 {
    EARTH_AXIAL_TILT * deg_to_rad(360.0 * ((284 + n) as f64 / 365.0)).sin()
}

pub fn solar_zenith_angle(latitude: f64, declination: f64, hour_angle: f64) -> f64 {
    let lat_rad = deg_to_rad(latitude);
    let dec_rad = deg_to_rad(declination);
    let ha_rad = deg_to_rad(hour_angle);
    let cos_zenith =
        lat_rad.sin() * dec_rad.sin() + lat_rad.cos() * dec_rad.cos() * ha_rad.cos();
    rad_to_deg(cos_zenith.clamp(-1.0, 1.0).acos())
}

pub fn solar_altitude(zenith_angle: f64) -> f64 {
    90.0 - zenith_angle
}

pub fn solar_azimuth(latitude: f64, declination: f64, hour_angle: f64) -> f64 {
    let lat_rad = deg_to_rad(latitude);
    let dec_rad = deg_to_rad(declination);
    let ha_rad = deg_to_rad(hour_angle);
    let sin_az = -dec_rad.cos() * ha_rad.sin();
    let cos_az = dec_rad.sin() * lat_rad.cos() - dec_rad.cos() * lat_rad.sin() * ha_rad.cos();
    let az_rad = sin_az.atan2(cos_az);
    normalize_angle(rad_to_deg(az_rad))
}

pub fn solar_angles_at(
    latitude: f64,
    decl: f64,
    correction: f64,
    utc_hours: f64,
) -> (f64, f64, f64, f64, f64) {
    let lst = (utc_hours + correction).rem_euclid(24.0);
    let ha = hour_angle(lst);
    let z = solar_zenith_angle(latitude, decl, ha);
    let alt = solar_altitude(z);
    let azim = solar_azimuth(latitude, decl, ha);
    (lst, ha, z, alt, azim)
}

pub fn solar_position<Tz: TimeZone>(
    latitude: f64,
    longitude: f64,
    dt: &DateTime<Tz>,
) -> SolarPosition {
    let utc = dt.with_timezone(&Utc);
    let utc_hours = utc.hour() as f64 + utc.minute() as f64 / 60.0 + utc.second() as f64 / 3600.0;
    let n = day_of_year(utc.year(), utc.month(), utc.day());
    let eot = equation_of_time(n);
    let decl = solar_declination(n);
    let correction = utc_lst_correction(longitude, eot);
    let (lst, ha, zenith, alt, azim) = solar_angles_at(latitude, decl, correction, utc_hours);
    SolarPosition {
        day_of_year: n,
        declination: decl,
        equation_of_time: eot,
        local_solar_time: lst,
        hour_angle: ha,
        zenith,
        altitude: alt,
        azimuth: azim,
    }
}

pub fn single_axis_tilt(pos: &SolarPosition, latitude: f64) -> f64 {
    let ha_rad = deg_to_rad(pos.hour_angle);
    let lat_rad = deg_to_rad(latitude);
    rad_to_deg(ha_rad.tan().atan2(lat_rad.cos()))
}

pub fn dual_axis_angles(pos: &SolarPosition) -> DualAxisAngles {
    DualAxisAngles {
        tilt: pos.zenith,
        panel_azimuth: normalize_angle(pos.azimuth + 180.0),
    }
}

pub fn optimal_fixed_tilt(latitude: f64) -> f64 {
    0.76 * latitude.abs() + 3.1
}

pub fn seasonal_tilt_adjustment(latitude: f64, season: Season) -> f64 {
    match season {
        Season::Summer => latitude.abs() - 15.0,
        Season::Winter => latitude.abs() + 15.0,
        Season::Spring | Season::Fall => latitude.abs(),
    }
}

pub fn example_calculation() -> ExampleResult {
    use chrono_tz::America::Chicago;

    let latitude = 39.8;
    let longitude = -89.6;

    let dt = Chicago.with_ymd_and_hms(2026, 3, 21, 12, 0, 0).unwrap();

    let pos = solar_position(latitude, longitude, &dt);
    let sa = single_axis_tilt(&pos, latitude);
    let da = dual_axis_angles(&pos);
    let fixed_annual = optimal_fixed_tilt(latitude);

    println!("=== Solar Position Calculation Example ===");
    println!(
        "Location: Springfield, IL ({:.1}°N, {:.1}°W)",
        latitude, -longitude
    );
    println!("Date/Time: {}", dt);
    println!();
    println!("--- Solar Position ---");
    println!("Day of year: {}", pos.day_of_year);
    println!("Declination: {:.2}°", pos.declination);
    println!("Equation of Time: {:.2} minutes", pos.equation_of_time);
    println!("Local Solar Time: {:.2} hours", pos.local_solar_time);
    println!("Hour Angle: {:.2}°", pos.hour_angle);
    println!("Zenith Angle: {:.2}°", pos.zenith);
    println!("Altitude: {:.2}°", pos.altitude);
    println!(
        "Azimuth: {:.2}° (0°=N, 90°=E, 180°=S)",
        pos.azimuth
    );
    println!();
    println!("--- Optimal Panel Angles ---");
    println!("Single-axis tracker rotation: {:.2}°", sa);
    println!("Dual-axis tilt: {:.2}°", da.tilt);
    println!("Dual-axis panel azimuth: {:.2}°", da.panel_azimuth);
    println!("Fixed annual optimal tilt: {:.1}°", fixed_annual);
    println!();

    ExampleResult {
        solar_position: pos,
        single_axis_rotation: sa,
        dual_axis: da,
        fixed_optimal_tilt: fixed_annual,
    }
}
