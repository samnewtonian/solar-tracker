#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Season {
    Summer,
    Winter,
    Spring,
    Fall,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct SolarPosition {
    pub day_of_year: i32,
    pub declination: f64,
    pub equation_of_time: f64,
    pub local_solar_time: f64,
    pub hour_angle: f64,
    pub zenith: f64,
    pub altitude: f64,
    pub azimuth: f64,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct DualAxisAngles {
    pub tilt: f64,
    pub panel_azimuth: f64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct SunriseSunset {
    pub sunrise: i32,
    pub sunset: i32,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct SingleAxisEntry {
    pub minutes: i32,
    pub rotation: Option<f64>,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct DualAxisEntry {
    pub minutes: i32,
    pub tilt: Option<f64>,
    pub panel_azimuth: Option<f64>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct DayData<E> {
    pub day_of_year: i32,
    pub sunrise_minutes: i32,
    pub sunset_minutes: i32,
    pub entries: Vec<E>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct TableMetadata {
    pub generated_at: String,
    pub total_entries: usize,
    pub storage_estimate_kb: f64,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct LookupTableConfig {
    pub interval_minutes: i32,
    pub latitude: f64,
    pub longitude: f64,
    pub year: i32,
    pub sunrise_buffer_minutes: i32,
    pub sunset_buffer_minutes: i32,
}

impl Default for LookupTableConfig {
    fn default() -> Self {
        Self {
            interval_minutes: 5,
            latitude: 39.8,
            longitude: -89.6,
            year: 2026,
            sunrise_buffer_minutes: 30,
            sunset_buffer_minutes: 30,
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct LookupTable<E> {
    pub config: LookupTableConfig,
    pub days: Vec<DayData<E>>,
    pub metadata: TableMetadata,
}

pub type SingleAxisTable = LookupTable<SingleAxisEntry>;
pub type DualAxisTable = LookupTable<DualAxisEntry>;

#[derive(Debug, Clone, PartialEq)]
pub struct ExampleResult {
    pub solar_position: SolarPosition,
    pub single_axis_rotation: f64,
    pub dual_axis: DualAxisAngles,
    pub fixed_optimal_tilt: f64,
}
