"""Precomputed solar angle lookup table generation and access.

Generates daylight-only tables for single-axis and dual-axis trackers
with configurable interval and sunrise/sunset buffers.
Tables are indexed by UTC minutes.
"""

import datetime
import math
from types import SimpleNamespace
from typing import Callable

from . import angles
from ._types import (
    DayData,
    DualAxisEntry,
    LookupTable,
    LookupTableConfig,
    SingleAxisEntry,
    SunriseSunset,
    TableMetadata,
)

DEFAULT_CONFIG = LookupTableConfig()


def minutes_to_time(total_minutes: int) -> tuple[int, int]:
    """Convert minutes since midnight to (hour, minute)."""
    return (total_minutes // 60, total_minutes % 60)


def time_to_minutes(t: tuple[int, int]) -> int:
    """Convert (hour, minute) to minutes since midnight."""
    return t[0] * 60 + t[1]


def intervals_per_day(interval_minutes: int) -> int:
    """Calculate number of intervals in a day."""
    return 1440 // interval_minutes


def doy_to_month_day(year: int, doy: int) -> tuple[int, int]:
    """Convert day-of-year to (month, day) for a given year."""
    remaining = doy
    for month_idx, dim in enumerate(angles.days_in_months(year)):
        if remaining <= dim:
            return (month_idx + 1, remaining)
        remaining -= dim
    return (12, 31)  # shouldn't reach here for valid input


def estimate_sunrise_sunset(latitude: float, day_of_year: int) -> SunriseSunset:
    """Estimate sunrise and sunset times for a given day.

    Returns SunriseSunset with sunrise/sunset as minutes from midnight (local solar time).
    Uses the hour angle at sunrise/sunset formula: cos(h) = -tan(lat) * tan(decl)
    """
    lat_rad = angles.deg_to_rad(latitude)
    decl = angles.solar_declination(day_of_year)
    decl_rad = angles.deg_to_rad(decl)
    cos_h = -1.0 * math.tan(lat_rad) * math.tan(decl_rad)

    if cos_h >= 1.0:
        # Polar night: sun never rises
        return SunriseSunset(sunrise=720, sunset=720)
    elif cos_h <= -1.0:
        # Polar day: sun never sets
        return SunriseSunset(sunrise=0, sunset=1440)
    else:
        h_deg = angles.rad_to_deg(math.acos(cos_h))
        half_day_minutes = (h_deg / 15.0) * 60.0
        solar_noon_minutes = 720
        return SunriseSunset(
            sunrise=int(solar_noon_minutes - half_day_minutes),
            sunset=int(solar_noon_minutes + half_day_minutes),
        )


def interpolate_angle(
    a1: float | None, a2: float | None, fraction: float
) -> float | None:
    """Interpolate between two angles, handling 360 deg wraparound."""
    if a1 is None or a2 is None:
        return None
    diff = a2 - a1
    if diff > 180:
        adjusted_diff = diff - 360
    elif diff < -180:
        adjusted_diff = diff + 360
    else:
        adjusted_diff = diff
    return (a1 + adjusted_diff * fraction) % 360.0


def _interpolate_linear(
    v1: float | None, v2: float | None, fraction: float
) -> float | None:
    """Simple linear interpolation between two values."""
    if v1 is None or v2 is None:
        return None
    return v1 + fraction * (v2 - v1)


def _compute_angles_fast(sin_lat, cos_lat, sin_dec, cos_dec, correction, utc_hours):
    """Compute solar angles using precomputed trig values for table generation."""
    lst = (utc_hours + correction) % 24.0
    ha = angles.DEGREES_PER_HOUR * (lst - 12.0)
    ha_rad = angles.deg_to_rad(ha)
    cos_z = sin_lat * sin_dec + cos_lat * cos_dec * math.cos(ha_rad)
    zenith = angles.rad_to_deg(math.acos(max(-1.0, min(1.0, cos_z))))
    sin_az = -1.0 * cos_dec * math.sin(ha_rad)
    cos_az = sin_dec * cos_lat - cos_dec * sin_lat * math.cos(ha_rad)
    azim = angles.normalize_angle(angles.rad_to_deg(math.atan2(sin_az, cos_az)))
    return SimpleNamespace(
        local_solar_time=lst,
        hour_angle=ha,
        zenith=zenith,
        altitude=90.0 - zenith,
        azimuth=azim,
    )


def _generate_table(
    config: LookupTableConfig,
    entry_fn: Callable,
    bytes_per_entry: int,
) -> LookupTable:
    """Shared table generation with UTC-indexed minutes."""
    n_intervals = intervals_per_day(config.interval_minutes)
    n_days = 366 if angles.leap_year(config.year) else 365
    days: list[DayData] = []

    lat_rad = angles.deg_to_rad(config.latitude)
    sin_lat = math.sin(lat_rad)
    cos_lat = math.cos(lat_rad)

    for doy in range(1, n_days + 1):
        ss = estimate_sunrise_sunset(config.latitude, doy)
        eot = angles.equation_of_time(doy)
        decl = angles.solar_declination(doy)
        dec_rad = angles.deg_to_rad(decl)
        sin_dec = math.sin(dec_rad)
        cos_dec = math.cos(dec_rad)
        correction = angles.utc_lst_correction(config.longitude, eot)
        correction_minutes = correction * 60.0

        sunrise_utc = int(ss.sunrise - correction_minutes)
        sunset_utc = int(ss.sunset - correction_minutes)

        start_minute = max(0, sunrise_utc - config.sunrise_buffer_minutes)
        end_minute = min(1439, sunset_utc + config.sunset_buffer_minutes)

        # Ceiling division: first entry must be >= start_minute
        first_interval = -(-start_minute // config.interval_minutes)
        last_interval = min(end_minute // config.interval_minutes, n_intervals - 1)

        entries = []
        for interval in range(first_interval, last_interval + 1):
            minutes = interval * config.interval_minutes
            utc_hours = minutes / 60.0
            pos = _compute_angles_fast(
                sin_lat, cos_lat, sin_dec, cos_dec, correction, utc_hours
            )
            local_minutes = int(minutes + correction_minutes)
            is_daylight = ss.sunrise <= local_minutes <= ss.sunset
            entries.append(entry_fn(minutes, pos, is_daylight))

        days.append(
            DayData(
                day_of_year=doy,
                sunrise_minutes=ss.sunrise,
                sunset_minutes=ss.sunset,
                entries=entries,
            )
        )

    total_entries = sum(len(d.entries) for d in days)
    storage_kb = (total_entries * bytes_per_entry) / 1024.0

    return LookupTable(
        config=config,
        days=days,
        metadata=TableMetadata(
            generated_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
            total_entries=total_entries,
            storage_estimate_kb=storage_kb,
        ),
    )


def generate_single_axis_table(config: LookupTableConfig) -> LookupTable:
    """Generate a single-axis tracker lookup table."""
    latitude = config.latitude

    def entry_fn(minutes, pos, is_daylight):
        rotation = angles.single_axis_tilt(pos, latitude) if is_daylight else None
        return SingleAxisEntry(minutes=minutes, rotation=rotation)

    return _generate_table(config, entry_fn, 4)


def generate_dual_axis_table(config: LookupTableConfig) -> LookupTable:
    """Generate a dual-axis tracker lookup table."""

    def entry_fn(minutes, pos, is_daylight):
        if is_daylight:
            da = angles.dual_axis_angles(pos)
            return DualAxisEntry(
                minutes=minutes, tilt=da.tilt, panel_azimuth=da.panel_azimuth
            )
        return DualAxisEntry(minutes=minutes, tilt=None, panel_azimuth=None)

    return _generate_table(config, entry_fn, 8)


def _find_bracketing_entries(
    entries: list, interval_minutes: int, minutes: int
) -> tuple | None:
    """Find the two entries bracketing the given minutes value.

    Returns (entry_before, entry_after, fraction) or None if outside range.
    """
    if not entries:
        return None
    first_minutes = entries[0].minutes
    last_minutes = entries[-1].minutes
    if minutes < first_minutes or minutes > last_minutes:
        return None

    idx_before = min(
        (minutes - first_minutes) // interval_minutes, len(entries) - 1
    )
    entry_before = entries[idx_before]
    entry_after = entries[idx_before + 1] if idx_before + 1 < len(entries) else None
    t0 = entry_before.minutes

    if entry_after is None or minutes == t0:
        return (entry_before, None, 0.0)

    t1 = entry_after.minutes
    fraction = (minutes - t0) / (t1 - t0)
    return (entry_before, entry_after, fraction)


def lookup_single_axis(
    table: LookupTable, day_of_year: int, minutes: int
) -> SingleAxisEntry | None:
    """Look up single-axis rotation from table with linear interpolation."""
    entries = table.days[day_of_year - 1].entries
    interval_minutes = table.config.interval_minutes
    result = _find_bracketing_entries(entries, interval_minutes, minutes)
    if result is None:
        return None
    before, after, fraction = result
    if after is None:
        return SingleAxisEntry(minutes=minutes, rotation=before.rotation)
    return SingleAxisEntry(
        minutes=minutes,
        rotation=_interpolate_linear(before.rotation, after.rotation, fraction),
    )


def lookup_dual_axis(
    table: LookupTable, day_of_year: int, minutes: int
) -> DualAxisEntry | None:
    """Look up dual-axis angles from table with linear interpolation.

    Uses interpolate_angle for panel_azimuth to handle 360 deg wraparound.
    """
    entries = table.days[day_of_year - 1].entries
    interval_minutes = table.config.interval_minutes
    result = _find_bracketing_entries(entries, interval_minutes, minutes)
    if result is None:
        return None
    before, after, fraction = result
    if after is None:
        return DualAxisEntry(
            minutes=minutes, tilt=before.tilt, panel_azimuth=before.panel_azimuth
        )
    return DualAxisEntry(
        minutes=minutes,
        tilt=_interpolate_linear(before.tilt, after.tilt, fraction),
        panel_azimuth=interpolate_angle(
            before.panel_azimuth, after.panel_azimuth, fraction
        ),
    )


def table_to_compact(table: LookupTable) -> list:
    """Strip metadata and return nested lists of angle values.

    For single-axis tables (entries have rotation):
      [[rotation ...] ...]

    For dual-axis tables (entries have tilt and panel_azimuth):
      [[[tilt, panel_azimuth] ...] ...]
    """
    sample_entry = table.days[0].entries[0]
    if isinstance(sample_entry, SingleAxisEntry):
        return [[e.rotation for e in day.entries] for day in table.days]
    else:
        return [
            [[e.tilt, e.panel_azimuth] for e in day.entries] for day in table.days
        ]
