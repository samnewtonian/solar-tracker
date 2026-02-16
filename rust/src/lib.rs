pub mod angles;
pub mod lookup_table;
pub mod types;

pub use angles::{
    day_of_year, deg_to_rad, dual_axis_angles, equation_of_time, example_calculation, hour_angle,
    intermediate_angle_b, local_solar_time, normalize_angle, optimal_fixed_tilt, rad_to_deg,
    seasonal_tilt_adjustment, single_axis_tilt, solar_altitude, solar_azimuth, solar_declination,
    solar_position, solar_zenith_angle, DEGREES_PER_HOUR, EARTH_AXIAL_TILT,
};

pub use lookup_table::{
    doy_to_month_day, dual_axis_table_to_compact, estimate_sunrise_sunset,
    generate_dual_axis_table, generate_single_axis_table, interpolate_angle, intervals_per_day,
    lookup_dual_axis, lookup_single_axis, minutes_to_time, single_axis_table_to_compact,
    time_to_minutes,
};

pub use types::{
    DayData, DualAxisAngles, DualAxisEntry, DualAxisTable, ExampleResult, LookupTable,
    LookupTableConfig, Season, SingleAxisEntry, SingleAxisTable, SolarPosition, SunriseSunset,
    TableMetadata,
};
