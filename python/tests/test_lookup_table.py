"""Port of lookup_table_test.clj — lookup table tests."""

import pytest

from solar_tracker._types import (
    DualAxisEntry,
    LookupTableConfig,
    SingleAxisEntry,
)
from solar_tracker.angles import day_of_year
from solar_tracker.lookup_table import (
    DEFAULT_CONFIG,
    doy_to_month_day,
    estimate_sunrise_sunset,
    generate_dual_axis_table,
    generate_single_axis_table,
    interpolate_angle,
    intervals_per_day,
    lookup_dual_axis,
    lookup_single_axis,
    minutes_to_time,
    table_to_compact,
    time_to_minutes,
)


class TestConfigValidation:
    def test_default_config_has_correct_defaults(self):
        c = LookupTableConfig()
        assert c.interval_minutes == 5
        assert c.latitude == 39.8
        assert c.longitude == -89.6
        assert c.std_meridian == -90.0
        assert c.year == 2026
        assert c.sunrise_buffer_minutes == 30
        assert c.sunset_buffer_minutes == 30


class TestTimeUtilities:
    @pytest.mark.parametrize("m", [0, 1, 59, 60, 61, 120, 719, 720, 721, 1439])
    def test_roundtrip(self, m):
        assert time_to_minutes(minutes_to_time(m)) == m

    def test_known_conversions(self):
        assert minutes_to_time(0) == (0, 0)
        assert minutes_to_time(720) == (12, 0)
        assert minutes_to_time(1439) == (23, 59)
        assert minutes_to_time(390) == (6, 30)


class TestIntervalsPerDay:
    @pytest.mark.parametrize(
        "interval,expected",
        [(5, 288), (15, 96), (30, 48), (1, 1440)],
    )
    def test_values(self, interval, expected):
        assert intervals_per_day(interval) == expected


class TestDoyMonthDay:
    @pytest.mark.parametrize(
        "year,month,day",
        [
            (2026, 1, 1),
            (2026, 3, 21),
            (2026, 6, 21),
            (2026, 12, 31),
            (2024, 2, 29),
            (2024, 3, 1),
            (2026, 7, 4),
            (2026, 11, 15),
        ],
    )
    def test_roundtrip(self, year, month, day):
        doy = day_of_year(year, month, day)
        m, d = doy_to_month_day(year, doy)
        assert (m, d) == (month, day), f"{year}-{month}-{day} (doy={doy})"

    def test_boundary_days(self):
        assert doy_to_month_day(2026, 1) == (1, 1)
        assert doy_to_month_day(2026, 365) == (12, 31)
        assert doy_to_month_day(2024, 366) == (12, 31)


class TestSunriseSunsetEstimation:
    def test_equinox_12h_daylight(self):
        ss = estimate_sunrise_sunset(39.8, 80)
        midpoint = (ss.sunrise + ss.sunset) / 2.0
        assert midpoint == pytest.approx(720.0, abs=30.0)
        daylight = ss.sunset - ss.sunrise
        assert daylight == pytest.approx(720.0, abs=60.0)

    def test_summer_longer_than_equinox(self):
        equinox = estimate_sunrise_sunset(39.8, 80)
        solstice = estimate_sunrise_sunset(39.8, 172)
        assert (solstice.sunset - solstice.sunrise) > (equinox.sunset - equinox.sunrise)

    def test_winter_shorter_than_equinox(self):
        equinox = estimate_sunrise_sunset(39.8, 80)
        winter = estimate_sunrise_sunset(39.8, 355)
        assert (winter.sunset - winter.sunrise) < (equinox.sunset - equinox.sunrise)

    def test_polar_day(self):
        ss = estimate_sunrise_sunset(80.0, 172)
        assert ss.sunrise == 0
        assert ss.sunset == 1440

    def test_polar_night(self):
        ss = estimate_sunrise_sunset(80.0, 355)
        assert ss.sunrise == ss.sunset


class TestSingleAxisOneDay:
    @pytest.fixture
    def table(self):
        config = LookupTableConfig(interval_minutes=15)
        return generate_single_axis_table(config)

    def test_day_80_structure(self, table):
        day_80 = table.days[79]
        assert day_80.day_of_year == 80
        assert len(day_80.entries) > 0

    def test_entries_have_required_fields(self, table):
        for entry in table.days[79].entries:
            assert isinstance(entry, SingleAxisEntry)

    def test_rotation_near_zero_at_noon(self, table):
        noon_entries = [e for e in table.days[79].entries if e.minutes == 720]
        if noon_entries:
            assert noon_entries[0].rotation is not None
            assert noon_entries[0].rotation == pytest.approx(0.0, abs=5.0)

    def test_morning_negative_afternoon_positive(self, table):
        entries = table.days[79].entries
        morning = next(
            (e for e in entries if e.rotation is not None and e.minutes < 600), None
        )
        afternoon = next(
            (e for e in entries if e.rotation is not None and e.minutes > 840), None
        )
        if morning:
            assert morning.rotation < 0.0
        if afternoon:
            assert afternoon.rotation > 0.0


class TestDualAxisOneDay:
    @pytest.fixture
    def table(self):
        config = LookupTableConfig(interval_minutes=15)
        return generate_dual_axis_table(config)

    def test_day_80_structure(self, table):
        day_80 = table.days[79]
        assert day_80.day_of_year == 80
        assert len(day_80.entries) > 0

    def test_entries_have_required_fields(self, table):
        for entry in table.days[79].entries:
            assert isinstance(entry, DualAxisEntry)

    def test_tilt_matches_zenith_at_noon(self, table):
        noon_entries = [e for e in table.days[79].entries if e.minutes == 720]
        if noon_entries:
            assert noon_entries[0].tilt is not None
            assert noon_entries[0].tilt == pytest.approx(40.0, abs=5.0)


class TestFullYearGeneration:
    @pytest.fixture(scope="class")
    def table(self):
        config = LookupTableConfig(interval_minutes=30)
        return generate_single_axis_table(config)

    def test_365_days(self, table):
        assert len(table.days) == 365

    def test_has_entries(self, table):
        assert table.metadata.total_entries > 0
        assert table.metadata.storage_estimate_kb > 0

    def test_every_day_has_entries(self, table):
        for day in table.days:
            assert len(day.entries) > 0, f"Day {day.day_of_year} has no entries"

    def test_entry_structure_consistent(self, table):
        sample_day = table.days[171]  # summer solstice
        for entry in sample_day.entries:
            assert isinstance(entry.minutes, int)
            assert entry.rotation is None or isinstance(entry.rotation, (int, float))


class TestLookupSingleAxis:
    @pytest.fixture(scope="class")
    def table(self):
        config = LookupTableConfig(interval_minutes=15)
        return generate_single_axis_table(config)

    def test_exact_boundary(self, table):
        result = lookup_single_axis(table, 80, 720)
        assert result is not None
        assert result.minutes == 720
        assert result.rotation is not None

    def test_interpolated(self, table):
        result = lookup_single_axis(table, 80, 727)
        at_720 = lookup_single_axis(table, 80, 720)
        at_735 = lookup_single_axis(table, 80, 735)
        assert result is not None
        assert result.minutes == 727
        if at_720.rotation is not None and at_735.rotation is not None and result.rotation is not None:
            lo = min(at_720.rotation, at_735.rotation)
            hi = max(at_720.rotation, at_735.rotation)
            assert lo - 0.01 <= result.rotation <= hi + 0.01


class TestLookupDualAxis:
    @pytest.fixture(scope="class")
    def table(self):
        config = LookupTableConfig(interval_minutes=15)
        return generate_dual_axis_table(config)

    def test_exact_boundary(self, table):
        result = lookup_dual_axis(table, 80, 720)
        assert result is not None
        assert result.tilt is not None
        assert result.panel_azimuth is not None

    def test_interpolated(self, table):
        result = lookup_dual_axis(table, 80, 727)
        assert result is not None
        assert result.tilt is not None
        assert result.panel_azimuth is not None


class TestLookupOutsideRange:
    def test_nighttime_returns_none(self):
        config = LookupTableConfig(interval_minutes=15)
        table = generate_single_axis_table(config)
        assert lookup_single_axis(table, 80, 0) is None
        assert lookup_single_axis(table, 80, 120) is None


class TestCompactExport:
    def test_single_axis(self):
        config = LookupTableConfig(interval_minutes=30)
        table = generate_single_axis_table(config)
        compact = table_to_compact(table)
        assert len(compact) == 365
        assert isinstance(compact, list)
        assert isinstance(compact[0], list)
        for day_vals in compact:
            for v in day_vals:
                assert v is None or isinstance(v, (int, float))

    def test_dual_axis(self):
        config = LookupTableConfig(interval_minutes=30)
        table = generate_dual_axis_table(config)
        compact = table_to_compact(table)
        assert len(compact) == 365
        assert isinstance(compact, list)
        sample = next(v for v in compact[0] if v is not None)
        assert isinstance(sample, list)
        assert len(sample) == 2


class TestInterpolateAngleWraparound:
    def test_normal_interpolation(self):
        assert interpolate_angle(0.0, 90.0, 0.5) == pytest.approx(45.0, abs=0.01)
        assert interpolate_angle(0.0, 90.0, 0.0) == pytest.approx(0.0, abs=0.01)
        assert interpolate_angle(0.0, 90.0, 1.0) == pytest.approx(90.0, abs=0.01)

    def test_wraparound_350_to_10(self):
        result = interpolate_angle(350.0, 10.0, 0.5)
        assert result == pytest.approx(0.0, abs=0.01)

    def test_wraparound_10_to_350(self):
        result = interpolate_angle(10.0, 350.0, 0.5)
        assert result == pytest.approx(0.0, abs=0.01)

    def test_returns_none_for_nil_input(self):
        assert interpolate_angle(None, 10.0, 0.5) is None
        assert interpolate_angle(10.0, None, 0.5) is None
