import { clans, schedules } from "./config";
export const KST = "Asia/Seoul";
export function gatheringDate(now = new Date()) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: KST,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
}
export function dayStart(day: string) {
  return Date.parse(day + "T00:00:00+09:00");
}
export function nextDay(day: string) {
  return gatheringDate(new Date(dayStart(day) + 86400000));
}
export function windows(day: string, defs = schedules, now = Date.now()) {
  return defs
    .map((s) => ({
      ...s,
      startAt: Date.parse(day + "T" + s.start + ":00+09:00"),
      endAt: Date.parse(day + "T" + s.end + ":00+09:00"),
    }))
    .map((s) => ({
      ...s,
      status: now < s.startAt ? "UPCOMING" : now < s.endAt ? "LIVE" : "ENDED",
    }));
}
export function sortQuiz(
  answers: number[],
  scoring: number[][][],
  tie?: string,
) {
  if (
    answers.length !== 8 ||
    answers.some((a) => !Number.isInteger(a) || a < 0 || a > 3)
  )
    throw Error("Answer all eight questions.");
  const scores = [0, 0, 0, 0];
  answers.forEach((a, q) => scoring[q][a].forEach((s, c) => (scores[c] += s)));
  const top = clans
    .filter((_, i) => scores[i] === Math.max(...scores))
    .map((c) => c.id);
  if (top.length === 1) return { clan: top[0], tied: [] };
  return tie && top.includes(tie)
    ? { clan: tie, tied: [] }
    : { clan: null, tied: top };
}
export function percentage(verified: number, eligible: number) {
  return eligible > 0 ? Math.min(100, (verified / eligible) * 100) : 0;
}
export function leader(stats: { clan: string; percentage: number }[]) {
  const max = Math.max(0, ...stats.map((s) => s.percentage));
  return max === 0 ? [] : stats.filter((s) => s.percentage === max).map((s) => s.clan);
}
