"""Demonstrate solar position calculations for Springfield, IL on March 21 at solar noon."""

from datetime import datetime
from zoneinfo import ZoneInfo

from solar_tracker.angles import (
    dual_axis_angles,
    optimal_fixed_tilt,
    single_axis_tilt,
    solar_position,
)


def main():
    latitude = 39.8
    longitude = -89.6

    dt = datetime(2026, 3, 21, 12, 0, tzinfo=ZoneInfo("America/Chicago"))

    pos = solar_position(latitude, longitude, dt)
    sa = single_axis_tilt(pos, latitude)
    da = dual_axis_angles(pos)
    fixed_annual = optimal_fixed_tilt(latitude)

    print("=== Solar Position Calculation Example ===")
    print(f"Location: Springfield, IL ({latitude:.1f}°N, {-longitude:.1f}°W)")
    print(f"Date/Time: {dt}")
    print()
    print("--- Solar Position ---")
    print(f"Day of year: {pos.day_of_year}")
    print(f"Declination: {pos.declination:.2f}°")
    print(f"Equation of Time: {pos.equation_of_time:.2f} minutes")
    print(f"Local Solar Time: {pos.local_solar_time:.2f} hours")
    print(f"Hour Angle: {pos.hour_angle:.2f}°")
    print(f"Zenith Angle: {pos.zenith:.2f}°")
    print(f"Altitude: {pos.altitude:.2f}°")
    print(f"Azimuth: {pos.azimuth:.2f}° (0°=N, 90°=E, 180°=S)")
    print()
    print("--- Optimal Panel Angles ---")
    print(f"Single-axis tracker rotation: {sa:.2f}°")
    print(f"Dual-axis tilt: {da.tilt:.2f}°")
    print(f"Dual-axis panel azimuth: {da.panel_azimuth:.2f}°")
    print(f"Fixed annual optimal tilt: {fixed_annual:.1f}°")


if __name__ == "__main__":
    main()
