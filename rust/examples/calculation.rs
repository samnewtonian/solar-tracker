use chrono_tz::America::Chicago;
use chrono::TimeZone;

use solar_tracker::angles::{
    dual_axis_angles, optimal_fixed_tilt, single_axis_tilt, solar_position,
};

fn main() {
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
}
