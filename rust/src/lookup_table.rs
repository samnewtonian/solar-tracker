use std::time::{SystemTime, UNIX_EPOCH};

use crate::angles;
use crate::types::{
    DayData, DualAxisEntry, DualAxisTable, LookupTable, LookupTableConfig, SingleAxisEntry,
    SingleAxisTable, SunriseSunset, TableMetadata,
};

pub fn minutes_to_time(total_minutes: i32) -> (i32, i32) {
    (total_minutes / 60, total_minutes % 60)
}

pub fn time_to_minutes(time: (i32, i32)) -> i32 {
    time.0 * 60 + time.1
}

pub fn intervals_per_day(interval_minutes: i32) -> i32 {
    1440 / interval_minutes
}

pub fn doy_to_month_day(year: i32, doy: i32) -> (i32, i32) {
    let leap = (year % 400 == 0) || (year % 4 == 0 && year % 100 != 0);
    let days_in_months = [
        31,
        if leap { 29 } else { 28 },
        31, 30, 31, 30, 31, 31, 30, 31, 30, 31,
    ];
    let mut remaining = doy;
    for (month_idx, &dim) in days_in_months.iter().enumerate() {
        if remaining <= dim {
            return (month_idx as i32 + 1, remaining);
        }
        remaining -= dim;
    }
    (12, 31)
}

pub fn estimate_sunrise_sunset(latitude: f64, day_of_year: i32) -> SunriseSunset {
    let lat_rad = angles::deg_to_rad(latitude);
    let decl = angles::solar_declination(day_of_year);
    let decl_rad = angles::deg_to_rad(decl);
    let cos_h = -lat_rad.tan() * decl_rad.tan();

    if cos_h >= 1.0 {
        SunriseSunset {
            sunrise: 720,
            sunset: 720,
        }
    } else if cos_h <= -1.0 {
        SunriseSunset {
            sunrise: 0,
            sunset: 1440,
        }
    } else {
        let h_deg = angles::rad_to_deg(cos_h.acos());
        let half_day_minutes = (h_deg / 15.0) * 60.0;
        let solar_noon_minutes = 720;
        SunriseSunset {
            sunrise: (solar_noon_minutes as f64 - half_day_minutes) as i32,
            sunset: (solar_noon_minutes as f64 + half_day_minutes) as i32,
        }
    }
}

pub fn interpolate_angle(a1: Option<f64>, a2: Option<f64>, fraction: f64) -> Option<f64> {
    let (v1, v2) = (a1?, a2?);
    let diff = v2 - v1;
    let adjusted_diff = if diff > 180.0 {
        diff - 360.0
    } else if diff < -180.0 {
        diff + 360.0
    } else {
        diff
    };
    Some((v1 + adjusted_diff * fraction).rem_euclid(360.0))
}

fn interpolate_linear(v1: Option<f64>, v2: Option<f64>, fraction: f64) -> Option<f64> {
    let a = v1?;
    let b = v2?;
    Some(a + fraction * (b - a))
}

trait HasMinutes {
    fn minutes(&self) -> i32;
}

impl HasMinutes for SingleAxisEntry {
    fn minutes(&self) -> i32 {
        self.minutes
    }
}

impl HasMinutes for DualAxisEntry {
    fn minutes(&self) -> i32 {
        self.minutes
    }
}

fn find_bracketing_entries<E: HasMinutes>(
    entries: &[E],
    interval_minutes: i32,
    minutes: i32,
) -> Option<(&E, Option<&E>, f64)> {
    if entries.is_empty() {
        return None;
    }
    let first_minutes = entries[0].minutes();
    let last_minutes = entries.last().unwrap().minutes();
    if minutes < first_minutes || minutes > last_minutes {
        return None;
    }

    let idx_before = ((minutes - first_minutes) / interval_minutes).min(entries.len() as i32 - 1) as usize;
    let entry_before = &entries[idx_before];
    let entry_after = entries.get(idx_before + 1);
    let t0 = entry_before.minutes();

    if entry_after.is_none() || minutes == t0 {
        return Some((entry_before, None, 0.0));
    }

    let t1 = entry_after.unwrap().minutes();
    let fraction = (minutes - t0) as f64 / (t1 - t0) as f64;
    Some((entry_before, entry_after, fraction))
}

fn generate_table<E, F>(config: &LookupTableConfig, entry_fn: F, bytes_per_entry: usize) -> LookupTable<E>
where
    F: Fn(i32, &crate::types::SolarPosition, bool) -> E,
{
    let n_intervals = intervals_per_day(config.interval_minutes);
    let mut days: Vec<DayData<E>> = Vec::with_capacity(365);

    for doy in 1..=365 {
        let ss = estimate_sunrise_sunset(config.latitude, doy);
        let start_minute = (ss.sunrise - config.sunrise_buffer_minutes).max(0);
        let end_minute = (ss.sunset + config.sunset_buffer_minutes).min(1439);
        let (month, day_of_month) = doy_to_month_day(config.year, doy);

        let first_interval = start_minute / config.interval_minutes;
        let last_interval = (end_minute / config.interval_minutes).min(n_intervals - 1);

        let capacity = (last_interval - first_interval + 1) as usize;
        let mut entries = Vec::with_capacity(capacity);
        for interval in first_interval..=last_interval {
            let mins = interval * config.interval_minutes;
            let (hour, minute) = minutes_to_time(mins);
            let pos = angles::solar_position(
                config.latitude,
                config.longitude,
                config.year,
                month,
                day_of_month,
                hour,
                minute,
                config.std_meridian,
            );
            let is_daylight = mins >= ss.sunrise && mins <= ss.sunset;
            entries.push(entry_fn(mins, &pos, is_daylight));
        }

        days.push(DayData {
            day_of_year: doy,
            sunrise_minutes: ss.sunrise,
            sunset_minutes: ss.sunset,
            entries,
        });
    }

    let total_entries: usize = days.iter().map(|d| d.entries.len()).sum();
    let storage_kb = (total_entries * bytes_per_entry) as f64 / 1024.0;

    let generated_at = format_utc_now();

    LookupTable {
        config: *config,
        days,
        metadata: TableMetadata {
            generated_at,
            total_entries,
            storage_estimate_kb: storage_kb,
        },
    }
}

fn format_utc_now() -> String {
    let duration = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default();
    let total_secs = duration.as_secs();

    // Days since epoch
    let days = total_secs / 86400;
    let day_secs = total_secs % 86400;
    let hour = day_secs / 3600;
    let min = (day_secs % 3600) / 60;
    let sec = day_secs % 60;

    // Convert days since 1970-01-01 to year/month/day
    let (year, month, day) = days_to_ymd(days);

    format!(
        "{:04}-{:02}-{:02}T{:02}:{:02}:{:02}+00:00",
        year, month, day, hour, min, sec
    )
}

fn days_to_ymd(days_since_epoch: u64) -> (i64, u64, u64) {
    // Algorithm from http://howardhinnant.github.io/date_algorithms.html
    let z = days_since_epoch as i64 + 719468;
    let era = if z >= 0 { z } else { z - 146096 } / 146097;
    let doe = (z - era * 146097) as u64;
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    let y = yoe as i64 + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    let y = if m <= 2 { y + 1 } else { y };
    (y, m, d)
}

pub fn generate_single_axis_table(config: &LookupTableConfig) -> SingleAxisTable {
    let latitude = config.latitude;
    generate_table(config, |minutes, pos, is_daylight| {
        let rotation = if is_daylight {
            Some(angles::single_axis_tilt(pos, latitude))
        } else {
            None
        };
        SingleAxisEntry { minutes, rotation }
    }, 4)
}

pub fn generate_dual_axis_table(config: &LookupTableConfig) -> DualAxisTable {
    generate_table(config, |minutes, pos, is_daylight| {
        if is_daylight {
            let da = angles::dual_axis_angles(pos);
            DualAxisEntry {
                minutes,
                tilt: Some(da.tilt),
                panel_azimuth: Some(da.panel_azimuth),
            }
        } else {
            DualAxisEntry {
                minutes,
                tilt: None,
                panel_azimuth: None,
            }
        }
    }, 8)
}

pub fn lookup_single_axis(
    table: &SingleAxisTable,
    day_of_year: i32,
    minutes: i32,
) -> Option<SingleAxisEntry> {
    let entries = &table.days[(day_of_year - 1) as usize].entries;
    let interval_minutes = table.config.interval_minutes;
    let (before, after, fraction) = find_bracketing_entries(entries, interval_minutes, minutes)?;
    match after {
        None => Some(SingleAxisEntry {
            minutes,
            rotation: before.rotation,
        }),
        Some(after) => Some(SingleAxisEntry {
            minutes,
            rotation: interpolate_linear(before.rotation, after.rotation, fraction),
        }),
    }
}

pub fn lookup_dual_axis(
    table: &DualAxisTable,
    day_of_year: i32,
    minutes: i32,
) -> Option<DualAxisEntry> {
    let entries = &table.days[(day_of_year - 1) as usize].entries;
    let interval_minutes = table.config.interval_minutes;
    let (before, after, fraction) = find_bracketing_entries(entries, interval_minutes, minutes)?;
    match after {
        None => Some(DualAxisEntry {
            minutes,
            tilt: before.tilt,
            panel_azimuth: before.panel_azimuth,
        }),
        Some(after) => Some(DualAxisEntry {
            minutes,
            tilt: interpolate_linear(before.tilt, after.tilt, fraction),
            panel_azimuth: interpolate_angle(
                before.panel_azimuth,
                after.panel_azimuth,
                fraction,
            ),
        }),
    }
}

pub fn single_axis_table_to_compact(table: &SingleAxisTable) -> Vec<Vec<Option<f64>>> {
    table
        .days
        .iter()
        .map(|day| day.entries.iter().map(|e| e.rotation).collect())
        .collect()
}

pub fn dual_axis_table_to_compact(
    table: &DualAxisTable,
) -> Vec<Vec<(Option<f64>, Option<f64>)>> {
    table
        .days
        .iter()
        .map(|day| {
            day.entries
                .iter()
                .map(|e| (e.tilt, e.panel_azimuth))
                .collect()
        })
        .collect()
}
