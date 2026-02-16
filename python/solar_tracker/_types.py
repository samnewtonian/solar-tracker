"""Frozen dataclasses for all structured return types."""

from dataclasses import dataclass
from enum import StrEnum


class Season(StrEnum):
    SUMMER = "summer"
    WINTER = "winter"
    SPRING = "spring"
    FALL = "fall"


@dataclass(frozen=True)
class SolarPosition:
    day_of_year: int
    declination: float
    equation_of_time: float
    local_solar_time: float
    hour_angle: float
    zenith: float
    altitude: float
    azimuth: float


@dataclass(frozen=True)
class DualAxisAngles:
    tilt: float
    panel_azimuth: float


@dataclass(frozen=True)
class SunriseSunset:
    sunrise: int
    sunset: int


@dataclass(frozen=True)
class SingleAxisEntry:
    minutes: int
    rotation: float | None


@dataclass(frozen=True)
class DualAxisEntry:
    minutes: int
    tilt: float | None
    panel_azimuth: float | None


@dataclass(frozen=True)
class DayData:
    day_of_year: int
    sunrise_minutes: int
    sunset_minutes: int
    entries: list


@dataclass(frozen=True)
class TableMetadata:
    generated_at: str
    total_entries: int
    storage_estimate_kb: float


@dataclass(frozen=True)
class LookupTableConfig:
    interval_minutes: int = 5
    latitude: float = 39.8
    longitude: float = -89.6
    year: int = 2026
    sunrise_buffer_minutes: int = 30
    sunset_buffer_minutes: int = 30


@dataclass(frozen=True)
class LookupTable:
    config: LookupTableConfig
    days: list[DayData]
    metadata: TableMetadata
