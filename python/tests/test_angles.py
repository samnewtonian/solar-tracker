"""Port of angles_test.clj — solar angle calculation tests."""

import math

import pytest

from solar_tracker._types import Season
from solar_tracker.angles import (
    day_of_year,
    deg_to_rad,
    dual_axis_angles,
    example_calculation,
    hour_angle,
    normalize_angle,
    optimal_fixed_tilt,
    rad_to_deg,
    seasonal_tilt_adjustment,
    single_axis_tilt,
    solar_declination,
    solar_position,
    solar_zenith_angle,
)


class TestDayOfYear:
    def test_known_dates(self):
        assert day_of_year(2026, 1, 1) == 1
        assert day_of_year(2026, 3, 21) == 80
        assert day_of_year(2026, 12, 31) == 365

    def test_leap_year(self):
        assert day_of_year(2024, 2, 29) == 60
        assert day_of_year(2024, 3, 1) == 61
        assert day_of_year(2024, 12, 31) == 366

    def test_century_leap_year_rules(self):
        assert day_of_year(2000, 2, 29) == 60  # divisible by 400
        assert day_of_year(1900, 2, 28) == 59  # not leap (div by 100 not 400)

    def test_first_day_of_each_month_non_leap(self):
        expected = [1, 32, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335]
        for month, exp in enumerate(expected, 1):
            assert day_of_year(2026, month, 1) == exp, f"Month {month}"

    def test_first_day_of_each_month_leap(self):
        expected = [1, 32, 61, 92, 122, 153, 183, 214, 245, 275, 306, 336]
        for month, exp in enumerate(expected, 1):
            assert day_of_year(2024, month, 1) == exp, f"Month {month} (leap)"


class TestNormalizeAngle:
    @pytest.mark.parametrize(
        "input_angle, expected",
        [
            (0.0, 0.0),
            (45.0, 45.0),
            (360.0, 0.0),
            (361.0, 1.0),
            (-1.0, 359.0),
            (-90.0, 270.0),
            (405.0, 45.0),
            (-180.0, 180.0),
        ],
    )
    def test_basic(self, input_angle, expected):
        assert normalize_angle(input_angle) == pytest.approx(expected, abs=0.1)

    @pytest.mark.parametrize(
        "input_angle, expected",
        [
            (720.0, 0.0),
            (810.0, 90.0),
            (-720.0, 0.0),
            (-450.0, 270.0),
        ],
    )
    def test_large_angles(self, input_angle, expected):
        assert normalize_angle(input_angle) == pytest.approx(expected, abs=0.1)

    def test_small_angles_near_zero(self):
        assert normalize_angle(0.001) == pytest.approx(0.001, abs=1e-6)
        assert normalize_angle(-0.001) == pytest.approx(359.999, abs=1e-6)


class TestSolarDeclination:
    def test_summer_solstice(self):
        assert solar_declination(172) == pytest.approx(23.45, abs=0.5)

    def test_winter_solstice(self):
        assert solar_declination(355) == pytest.approx(-23.45, abs=0.5)

    def test_spring_equinox(self):
        assert solar_declination(80) == pytest.approx(0.0, abs=1.0)

    def test_fall_equinox(self):
        assert solar_declination(264) == pytest.approx(0.0, abs=1.0)

    def test_bounded_all_days(self):
        for n in range(1, 366):
            decl = solar_declination(n)
            assert -23.45 <= decl <= 23.45, f"Day {n}: {decl}"


class TestSolarPositionSpringfieldEquinox:
    @pytest.fixture
    def pos(self):
        return solar_position(39.8, -89.6, 2026, 3, 21, 12, 0, -90.0)

    def test_day_of_year(self, pos):
        assert pos.day_of_year == 80

    def test_declination(self, pos):
        assert pos.declination == pytest.approx(0.0, abs=1.0)

    def test_equation_of_time(self, pos):
        assert pos.equation_of_time == pytest.approx(-7.5, abs=2.0)

    def test_zenith(self, pos):
        assert pos.zenith == pytest.approx(40.0, abs=2.0)

    def test_altitude(self, pos):
        assert pos.altitude == pytest.approx(50.0, abs=2.0)

    def test_azimuth(self, pos):
        assert 174.0 <= pos.azimuth <= 185.0


class TestSolarPositionSummerSolstice:
    def test_summer_solstice(self):
        pos = solar_position(39.8, -89.6, 2026, 6, 21, 12, 0, -90.0)
        assert pos.declination == pytest.approx(23.45, abs=1.0)
        assert pos.zenith < 40.0
        assert pos.altitude > 50.0


class TestSolarPositionWinterSolstice:
    def test_winter_solstice(self):
        pos = solar_position(39.8, -89.6, 2026, 12, 21, 12, 0, -90.0)
        assert pos.declination == pytest.approx(-23.45, abs=1.0)
        assert pos.zenith > 40.0
        assert pos.altitude < 50.0


class TestSingleAxisTilt:
    def test_near_zero_at_noon(self):
        pos = solar_position(39.8, -89.6, 2026, 3, 21, 12, 0, -90.0)
        assert single_axis_tilt(pos, 39.8) == pytest.approx(0.0, abs=5.0)

    def test_negative_in_morning(self):
        pos = solar_position(39.8, -89.6, 2026, 3, 21, 9, 0, -90.0)
        assert single_axis_tilt(pos, 39.8) < 0.0

    def test_positive_in_afternoon(self):
        pos = solar_position(39.8, -89.6, 2026, 3, 21, 15, 0, -90.0)
        assert single_axis_tilt(pos, 39.8) > 0.0


class TestDualAxisAngles:
    def test_tilt_equals_zenith(self):
        pos = solar_position(39.8, -89.6, 2026, 3, 21, 12, 0, -90.0)
        da = dual_axis_angles(pos)
        assert da.tilt == pytest.approx(pos.zenith, abs=0.01)

    def test_panel_azimuth_opposite_sun(self):
        pos = solar_position(39.8, -89.6, 2026, 3, 21, 12, 0, -90.0)
        da = dual_axis_angles(pos)
        assert (354.0 <= da.panel_azimuth <= 360.0) or (0.0 <= da.panel_azimuth <= 5.0)


class TestOptimalFixedTilt:
    def test_springfield(self):
        assert optimal_fixed_tilt(39.8) == pytest.approx(33.3, abs=1.0)

    def test_formula_values(self):
        assert optimal_fixed_tilt(40.0) == pytest.approx(33.5, abs=0.1)
        assert optimal_fixed_tilt(0.0) == pytest.approx(3.1, abs=0.1)
        assert optimal_fixed_tilt(-40.0) == pytest.approx(33.5, abs=0.1)

    def test_symmetric(self):
        for lat in [10.0, 25.0, 40.0, 55.0, 70.0, 85.0]:
            assert optimal_fixed_tilt(lat) == pytest.approx(
                optimal_fixed_tilt(-lat), abs=1e-10
            )

    def test_increases_with_latitude(self):
        tilts = [optimal_fixed_tilt(lat) for lat in [0, 15, 30, 45, 60, 75, 90]]
        assert tilts == sorted(tilts)


class TestSeasonalTiltAdjustment:
    def test_basic(self):
        lat = 40.0
        assert seasonal_tilt_adjustment(lat, Season.SUMMER) == pytest.approx(25.0, abs=0.1)
        assert seasonal_tilt_adjustment(lat, Season.WINTER) == pytest.approx(55.0, abs=0.1)
        assert seasonal_tilt_adjustment(lat, Season.SPRING) == pytest.approx(40.0, abs=0.1)
        assert seasonal_tilt_adjustment(lat, Season.FALL) == pytest.approx(40.0, abs=0.1)

    def test_ordering(self):
        lat = 40.0
        assert (
            seasonal_tilt_adjustment(lat, Season.SUMMER)
            < seasonal_tilt_adjustment(lat, Season.SPRING)
            < seasonal_tilt_adjustment(lat, Season.WINTER)
        )

    def test_equator(self):
        assert seasonal_tilt_adjustment(0.0, Season.SUMMER) == pytest.approx(-15.0, abs=0.01)
        assert seasonal_tilt_adjustment(0.0, Season.WINTER) == pytest.approx(15.0, abs=0.01)
        assert seasonal_tilt_adjustment(0.0, Season.SPRING) == pytest.approx(0.0, abs=0.01)


class TestExampleCalculation:
    def test_runs_without_error(self):
        result = example_calculation()
        assert isinstance(result, dict)
        assert "solar_position" in result
        assert "single_axis_rotation" in result
        assert "dual_axis" in result
        assert "fixed_optimal_tilt" in result


class TestHourAngleProperties:
    def test_solar_noon(self):
        assert hour_angle(12.0) == pytest.approx(0.0, abs=0.01)

    def test_morning_negative(self):
        assert hour_angle(10.0) < 0.0

    def test_afternoon_positive(self):
        assert hour_angle(14.0) > 0.0

    def test_known_values(self):
        assert hour_angle(13.0) == pytest.approx(15.0, abs=0.01)
        assert hour_angle(11.0) == pytest.approx(-15.0, abs=0.01)
        assert hour_angle(15.0) == pytest.approx(45.0, abs=0.01)


class TestDegRadRoundtrip:
    @pytest.mark.parametrize(
        "deg", [0.0, 45.0, 90.0, 180.0, 270.0, 360.0, -45.0, -180.0, 123.456]
    )
    def test_roundtrip(self, deg):
        assert rad_to_deg(deg_to_rad(deg)) == pytest.approx(deg, abs=1e-10)

    def test_known_conversions(self):
        assert deg_to_rad(180.0) == pytest.approx(math.pi, abs=1e-10)
        assert deg_to_rad(90.0) == pytest.approx(math.pi / 2, abs=1e-10)
        assert deg_to_rad(0.0) == pytest.approx(0.0, abs=1e-10)
        assert rad_to_deg(math.pi) == pytest.approx(180.0, abs=1e-10)


class TestEquatorSolarNoonEquinox:
    def test_sun_overhead(self):
        pos = solar_position(0.0, 0.0, 2026, 3, 21, 12, 0, 0.0)
        assert pos.declination == pytest.approx(0.0, abs=1.0)
        assert pos.zenith < 5.0
        assert pos.altitude > 85.0


class TestPolarLatitude:
    def test_summer(self):
        pos = solar_position(70.0, 15.0, 2026, 6, 21, 12, 0, 15.0)
        assert pos.altitude > 0.0
        assert pos.zenith < 90.0

    def test_winter(self):
        pos = solar_position(70.0, 15.0, 2026, 12, 21, 12, 0, 15.0)
        assert pos.zenith > 85.0


class TestSouthernHemisphere:
    def test_reversed_seasons(self):
        pos_jun = solar_position(-33.9, 151.2, 2026, 6, 21, 12, 0, 150.0)
        pos_dec = solar_position(-33.9, 151.2, 2026, 12, 21, 12, 0, 150.0)
        assert pos_jun.zenith > pos_dec.zenith
        assert pos_jun.altitude < pos_dec.altitude


class TestMidnightPosition:
    def test_below_horizon(self):
        pos = solar_position(39.8, -89.6, 2026, 3, 21, 0, 0, -90.0)
        assert pos.altitude < 0.0
        assert pos.zenith > 90.0


class TestZenithAltitudeComplement:
    @pytest.mark.parametrize(
        "lat,lon,yr,mo,dy,hr,mn,std",
        [
            (39.8, -89.6, 2026, 3, 21, 12, 0, -90.0),
            (0.0, 0.0, 2026, 6, 21, 12, 0, 0.0),
            (-33.9, 151.2, 2026, 12, 21, 15, 30, 150.0),
            (51.5, -0.1, 2026, 9, 22, 8, 0, 0.0),
            (70.0, 25.0, 2026, 6, 21, 18, 0, 30.0),
        ],
    )
    def test_complement(self, lat, lon, yr, mo, dy, hr, mn, std):
        pos = solar_position(lat, lon, yr, mo, dy, hr, mn, std)
        assert pos.zenith + pos.altitude == pytest.approx(90.0, abs=1e-10)


class TestAzimuthAlwaysNormalized:
    @pytest.mark.parametrize(
        "lat,lon,yr,mo,dy,hr,mn,std",
        [
            (39.8, -89.6, 2026, 1, 15, 8, 0, -90.0),
            (39.8, -89.6, 2026, 1, 15, 16, 0, -90.0),
            (39.8, -89.6, 2026, 7, 15, 6, 0, -90.0),
            (39.8, -89.6, 2026, 7, 15, 20, 0, -90.0),
            (-45.0, 170.0, 2026, 3, 21, 12, 0, 180.0),
            (60.0, 10.0, 2026, 6, 21, 3, 0, 15.0),
            (0.0, 0.0, 2026, 9, 22, 12, 0, 0.0),
        ],
    )
    def test_in_range(self, lat, lon, yr, mo, dy, hr, mn, std):
        pos = solar_position(lat, lon, yr, mo, dy, hr, mn, std)
        assert 0.0 <= pos.azimuth < 360.0


class TestEquationOfTimeBounded:
    def test_all_days(self):
        for n in range(1, 366):
            from solar_tracker.angles import equation_of_time

            eot = equation_of_time(n)
            assert -15.0 <= eot <= 17.0, f"Day {n}: {eot}"


class TestZenithNonNegative:
    @pytest.mark.parametrize(
        "lat,decl,ha",
        [
            (0.0, 0.0, 0.0),
            (45.0, 23.45, 0.0),
            (-45.0, -23.45, 0.0),
            (0.0, 23.45, 90.0),
            (89.0, 23.45, 0.0),
            (-89.0, -23.45, 0.0),
        ],
    )
    def test_in_range(self, lat, decl, ha):
        z = solar_zenith_angle(lat, decl, ha)
        assert 0.0 <= z <= 180.0


class TestDualAxisPanelAzimuthNormalized:
    @pytest.mark.parametrize(
        "solar_az", [0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0, 359.9]
    )
    def test_in_range(self, solar_az):
        from solar_tracker._types import SolarPosition

        pos = SolarPosition(
            day_of_year=1,
            declination=0.0,
            equation_of_time=0.0,
            local_solar_time=12.0,
            hour_angle=0.0,
            zenith=30.0,
            altitude=60.0,
            azimuth=solar_az,
        )
        da = dual_axis_angles(pos)
        assert 0.0 <= da.panel_azimuth < 360.0


class TestMultipleCitiesNoonEquinox:
    @pytest.mark.parametrize(
        "name,lat,lon,std",
        [
            ("London", 51.5, -0.1, 0.0),
            ("Tokyo", 35.7, 139.7, 135.0),
            ("Cape Town", -33.9, 18.4, 30.0),
            ("Quito", -0.2, -78.5, -75.0),
        ],
    )
    def test_zenith_approx_latitude(self, name, lat, lon, std):
        pos = solar_position(lat, lon, 2026, 3, 21, 12, 0, std)
        assert pos.zenith == pytest.approx(abs(lat), abs=8.0), name


class TestMorningAfternoonSymmetry:
    def test_symmetric(self):
        pos_9am = solar_position(39.8, -89.6, 2026, 3, 21, 9, 0, -90.0)
        pos_3pm = solar_position(39.8, -89.6, 2026, 3, 21, 15, 0, -90.0)
        assert pos_9am.zenith == pytest.approx(pos_3pm.zenith, abs=5.0)
        assert pos_9am.azimuth < 180.0
        assert pos_3pm.azimuth > 180.0
