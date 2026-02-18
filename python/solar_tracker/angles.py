"""Solar panel angle calculations for single and dual-axis tracking systems.

All angles in degrees unless otherwise noted.
"""

import math
from datetime import datetime as DateTime, timezone

from ._types import DualAxisAngles, Season, SolarPosition

EARTH_AXIAL_TILT = 23.45
DEGREES_PER_HOUR = 15.0


def deg_to_rad(deg: float) -> float:
    """Convert degrees to radians."""
    return deg * (math.pi / 180.0)


def rad_to_deg(rad: float) -> float:
    """Convert radians to degrees."""
    return rad * (180.0 / math.pi)


def normalize_angle(angle: float) -> float:
    """Normalize angle to 0-360 degree range."""
    return angle % 360.0


def leap_year(year: int) -> bool:
    """Returns True if year is a leap year."""
    return (year % 400 == 0) or (year % 4 == 0 and year % 100 != 0)


def days_in_months(year: int) -> list[int]:
    """Returns a list of days per month for the given year."""
    return [31, 29 if leap_year(year) else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]


def day_of_year(year: int, month: int, day: int) -> int:
    """Calculate day of year (1-366) from year, month, day."""
    return sum(days_in_months(year)[: month - 1]) + day


def intermediate_angle_b(n: int) -> float:
    """Calculate intermediate angle B used in equation of time.

    Input: n = day of year (1-365)
    Output: B in radians
    """
    return deg_to_rad((n - 1) * (360.0 / 365.0))


def equation_of_time(n: int) -> float:
    """Calculate the Equation of Time correction.

    Input: n = day of year (1-365)
    Output: correction in minutes
    """
    b = intermediate_angle_b(n)
    return 229.18 * (
        0.000075
        + 0.001868 * math.cos(b)
        - 0.032077 * math.sin(b)
        - 0.014615 * math.cos(2 * b)
        - 0.040849 * math.sin(2 * b)
    )


def utc_lst_correction(longitude: float, eot: float) -> float:
    """Calculate the UTC-to-local-solar-time correction in hours.

    Args:
        longitude: Observer's longitude (degrees, negative for West)
        eot: Equation of time in minutes

    Returns:
        Correction in hours to add to UTC to get local solar time
    """
    return (4.0 * longitude + eot) / 60.0


def hour_angle(local_solar_time: float) -> float:
    """Calculate the hour angle from local solar time.

    At solar noon: h = 0 degrees.
    Morning: h < 0 (sun is east).
    Afternoon: h > 0 (sun is west).
    Each hour = 15 degrees of Earth rotation.
    """
    return DEGREES_PER_HOUR * (local_solar_time - 12.0)


def solar_declination(n: int) -> float:
    """Calculate solar declination angle.

    Input: n = day of year (1-365)
    Output: declination in degrees

    Ranges from -23.45 deg (winter solstice) to +23.45 deg (summer solstice).
    """
    return EARTH_AXIAL_TILT * math.sin(deg_to_rad(360.0 * ((284 + n) / 365.0)))


def solar_zenith_angle(
    latitude: float, declination: float, hour_angle: float
) -> float:
    """Calculate the solar zenith angle.

    Returns zenith angle in degrees.
    """
    lat_rad = deg_to_rad(latitude)
    dec_rad = deg_to_rad(declination)
    ha_rad = deg_to_rad(hour_angle)
    cos_zenith = math.sin(lat_rad) * math.sin(dec_rad) + math.cos(
        lat_rad
    ) * math.cos(dec_rad) * math.cos(ha_rad)
    # Clamp to [-1, 1] to handle floating point errors
    return rad_to_deg(math.acos(max(-1.0, min(1.0, cos_zenith))))


def solar_altitude(zenith_angle: float) -> float:
    """Calculate solar altitude (elevation) angle. Complement of zenith."""
    return 90.0 - zenith_angle


def solar_azimuth(
    latitude: float,
    declination: float,
    hour_angle: float,
) -> float:
    """Calculate solar azimuth angle using atan2 for proper quadrant handling.

    Returns azimuth in degrees (0=North, 90=East, 180=South, 270=West).
    """
    lat_rad = deg_to_rad(latitude)
    dec_rad = deg_to_rad(declination)
    ha_rad = deg_to_rad(hour_angle)
    sin_az = -1.0 * math.cos(dec_rad) * math.sin(ha_rad)
    cos_az = math.sin(dec_rad) * math.cos(lat_rad) - math.cos(dec_rad) * math.sin(
        lat_rad
    ) * math.cos(ha_rad)
    az_rad = math.atan2(sin_az, cos_az)
    return normalize_angle(rad_to_deg(az_rad))


def solar_angles_at(
    latitude: float, decl: float, correction: float, utc_hours: float
) -> tuple[float, float, float, float, float]:
    """Compute solar angles from precomputed correction and UTC hours.

    Returns:
        (local_solar_time, hour_angle, zenith, altitude, azimuth)
    """
    lst = (utc_hours + correction) % 24.0
    ha = hour_angle(lst)
    z = solar_zenith_angle(latitude, decl, ha)
    alt = solar_altitude(z)
    azim = solar_azimuth(latitude, decl, ha)
    return lst, ha, z, alt, azim


def solar_position(
    latitude: float, longitude: float, dt: DateTime
) -> SolarPosition:
    """Calculate complete solar position for given location and timezone-aware datetime."""
    if dt.tzinfo is None:
        raise ValueError("dt must be timezone-aware")
    utc = dt.astimezone(timezone.utc)
    utc_hours = utc.hour + utc.minute / 60.0 + utc.second / 3600.0
    n = day_of_year(utc.year, utc.month, utc.day)
    eot = equation_of_time(n)
    decl = solar_declination(n)
    correction = utc_lst_correction(longitude, eot)
    lst, ha, z, alt, azim = solar_angles_at(latitude, decl, correction, utc_hours)
    return SolarPosition(
        day_of_year=n,
        declination=decl,
        equation_of_time=eot,
        local_solar_time=lst,
        hour_angle=ha,
        zenith=z,
        altitude=alt,
        azimuth=azim,
    )


def single_axis_tilt(pos: SolarPosition, latitude: float) -> float:
    """Calculate optimal tilt angle for single-axis (north-south) tracker.

    Returns rotation angle in degrees (positive = tilted toward west).
    """
    ha_rad = deg_to_rad(pos.hour_angle)
    lat_rad = deg_to_rad(latitude)
    return rad_to_deg(math.atan(math.tan(ha_rad) / math.cos(lat_rad)))


def dual_axis_angles(pos: SolarPosition) -> DualAxisAngles:
    """Calculate optimal angles for dual-axis tracker.

    Returns both the panel tilt and azimuth needed to point directly at the sun.
    """
    return DualAxisAngles(
        tilt=pos.zenith,
        panel_azimuth=normalize_angle(pos.azimuth + 180.0),
    )


def optimal_fixed_tilt(latitude: float) -> float:
    """Calculate optimal annual fixed tilt angle for a given latitude.

    Uses the empirical formula: tilt = 0.76 * |latitude| + 3.1 degrees
    """
    return 0.76 * abs(latitude) + 3.1


def seasonal_tilt_adjustment(latitude: float, season: Season) -> float:
    """Calculate seasonal tilt adjustment for fixed installations."""
    match season:
        case Season.SUMMER:
            return abs(latitude) - 15.0
        case Season.WINTER:
            return abs(latitude) + 15.0
        case Season.SPRING | Season.FALL:
            return abs(latitude)
        case _:
            raise ValueError(f"Unknown season: {season}")
