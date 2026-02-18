use std::time::{SystemTime, UNIX_EPOCH};

use crate::angles;
use crate::types::{
    DayData, DualAxisEntry, DualAxisTable, LookupTable, LookupTableConfig, SingleAxisEntry,
    SingleAxisTable, SolarPosition, SunriseSunset, TableMetadata,
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

pub fn doy_to_month_day(year: i32, doy: i32) -> (u32, u32) {
    let dim = angles::days_in_months(year);
    let mut remaining = doy as u32;
    for (month_idx, &d) in dim.iter().enumerate() {
        if remaining <= d {
            return (month_idx as u32 + 1, remaining);
        }
        remaining -= d;
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

fn compute_angles_fast(
    sin_lat: f64,
    cos_lat: f64,
    sin_dec: f64,
    cos_dec: f64,
    correction: f64,
    utc_hours: f64,
) -> SolarPosition {
    let lst = (utc_hours + correction).rem_euclid(24.0);
    let ha = angles::DEGREES_PER_HOUR * (lst - 12.0);
    let ha_rad = angles::deg_to_rad(ha);
    let cos_z = sin_lat * sin_dec + cos_lat * cos_dec * ha_rad.cos();
    let zenith = angles::rad_to_deg(cos_z.clamp(-1.0, 1.0).acos());
    let sin_az = -cos_dec * ha_rad.sin();
    let cos_az = sin_dec * cos_lat - cos_dec * sin_lat * ha_rad.cos();
    let azim = angles::normalize_angle(angles::rad_to_deg(sin_az.atan2(cos_az)));
    SolarPosition {
        day_of_year: 0,
        declination: 0.0,
        equation_of_time: 0.0,
        local_solar_time: lst,
        hour_angle: ha,
        zenith,
        altitude: 90.0 - zenith,
        azimuth: azim,
    }
}

fn generate_table<E, F>(config: &LookupTableConfig, entry_fn: F, bytes_per_entry: usize) -> LookupTable<E>
where
    F: Fn(i32, &SolarPosition, bool) -> E,
{
    let n_intervals = intervals_per_day(config.interval_minutes);
    let n_days = if angles::leap_year(config.year) { 366 } else { 365 };
    let mut days: Vec<DayData<E>> = Vec::with_capacity(n_days as usize);

    let lat_rad = angles::deg_to_rad(config.latitude);
    let sin_lat = lat_rad.sin();
    let cos_lat = lat_rad.cos();

    for doy in 1..=n_days {
        let ss = estimate_sunrise_sunset(config.latitude, doy);
        let eot = angles::equation_of_time(doy);
        let decl = angles::solar_declination(doy);
        let dec_rad = angles::deg_to_rad(decl);
        let sin_dec = dec_rad.sin();
        let cos_dec = dec_rad.cos();
        let correction = angles::utc_lst_correction(config.longitude, eot);
        let correction_minutes = correction * 60.0;

        let sunrise_utc = (ss.sunrise as f64 - correction_minutes) as i32;
        let sunset_utc = (ss.sunset as f64 - correction_minutes) as i32;

        let start_minute = 0.max(sunrise_utc - config.sunrise_buffer_minutes);
        let end_minute = 1439.min(sunset_utc + config.sunset_buffer_minutes);

        // Ceiling division for first interval
        let first_interval = (start_minute + config.interval_minutes - 1) / config.interval_minutes;
        let last_interval = (end_minute / config.interval_minutes).min(n_intervals - 1);

        let capacity = if last_interval >= first_interval {
            (last_interval - first_interval + 1) as usize
        } else {
            0
        };
        let mut entries = Vec::with_capacity(capacity);
        for interval in first_interval..=last_interval {
            let mins = interval * config.interval_minutes;
            let utc_hours = mins as f64 / 60.0;
            let pos = compute_angles_fast(
                sin_lat, cos_lat, sin_dec, cos_dec, correction, utc_hours,
            );
            let local_minutes = (mins as f64 + correction_minutes) as i32;
            let is_daylight = local_minutes >= ss.sunrise && local_minutes <= ss.sunset;
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
