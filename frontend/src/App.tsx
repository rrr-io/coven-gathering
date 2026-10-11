import { apiFetch, setAdmin, clearAdmin, openPrivateProof } from "./api";
import { useEffect, useRef, useState } from "react";
import {
  ArrowUpRight,
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  X,
  Globe2,
  Clock3,
  LockKeyhole,
  Check,
  Upload,
  ShieldCheck,
  Menu,
  Moon,
  Sun,
  Compass,
  Mountain,
  Users,
  ExternalLink,
  RefreshCw,
  AlertCircle,
} from "lucide-react";
import countries from "./lib/countries.json";
import { clans as defaults, defaultConfig, rules } from "./lib/config";
import { percentage, leader } from "./lib/engine";
const nav = [
  ["home", "Home"],
  ["clans", "Clans"],
  ["race", "Race"],
  ["worldwide", "Worldwide"],
  ["history", "History"],
  ["guide", "MAMA guide"],
  ["rules", "Rules"],
];
const cn = (id: string) => countries.find((c) => c.id === id);
function time(ms: number, zone = "Asia/Seoul", seconds = false) {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: zone,
    hour: "2-digit",
    minute: "2-digit",
    ...(seconds ? { second: "2-digit" } : {}),
  }).format(ms);
}
function offset(zone: string, now: number) {
  return new Intl.DateTimeFormat("en", {
    timeZone: zone,
    timeZoneName: "shortOffset",
  })
    .formatToParts(now)
    .find((p) => p.type === "timeZoneName")?.value;
}
function Crest({
  clan = "cullen",
  small = false,
}: {
  clan?: string;
  small?: boolean;
}) {
  const Icon =
    clan === "cullen"
      ? Moon
      : clan === "volturi"
        ? Sun
        : clan === "denali"
          ? Mountain
          : Compass;
  return (
    <div
      className={"crest " + (small ? "small" : "")}
      style={{ "--clan": defaults.find((c) => c.id === clan)?.color } as any}
    >
      <svg viewBox="0 0 120 140" aria-hidden="true">
        <path d="M60 4 108 28v52c0 26-28 42-48 55C40 122 12 106 12 80V28Z" />
        <path d="m60 13 40 21v44c0 22-25 38-40 48C45 116 20 100 20 78V34Z" />
        <path d="m60 0 4 7-4 7-4-7M5 54h17m76 0h17M60 126v14" />
      </svg>
      <Icon strokeWidth={1} aria-hidden="true" />
    </div>
  );
}
function Forest() {
  return (
    <svg
      className="forest"
      viewBox="0 0 1400 500"
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="mist" x2="0" y2="1">
          <stop stopColor="#374447" stopOpacity=".12" />
          <stop offset="1" stopColor="#0b1011" />
        </linearGradient>
      </defs>
      {[0, 1, 2].map((layer) => (
        <g
          key={layer}
          fill={["#1a2629", "#152023", "#0b1114"][layer]}
          opacity={0.45 + layer * 0.2}
        >
          {Array.from({ length: 32 }, (_, i) => {
            const h = 70 + ((i * 79 + layer * 41) % 190);
            const x = i * 49 - 30 + layer * 17;
            const y = 410 + layer * 30;
            return (
              <path
                key={i}
                d={`M${x} ${y - h}l-17 48h10l-28 56h17l-37 69h48v45h14v-45h47l-38-69h17l-27-56h10Z`}
              />
            );
          })}
        </g>
      ))}
    </svg>
  );
}
export default function Page() {
  const [adminUser, setAdminUser] = useState("admin"),
    [adminPassword, setAdminPassword] = useState("");
  const [data, setData] = useState<any>(null),
    [error, setError] = useState(""),
    [page, setPage] = useState("home"),
    [mobile, setMobile] = useState(false),
    [now, setNow] = useState(Date.now()),
    [skew, setSkew] = useState(0),
    [loading, setLoading] = useState(false),
    [notice, setNotice] = useState("");
  const [onboard, setOnboard] = useState(false),
    [step, setStep] = useState("country"),
    [country, setCountry] = useState(""),
    [search, setSearch] = useState(""),
    [answers, setAnswers] = useState<number[]>([]),
    [q, setQ] = useState(0),
    [result, setResult] = useState<any>(null),
    [tied, setTied] = useState<string[]>([]),
    [busy, setBusy] = useState(false),
    [quizError, setQuizError] = useState("");
  const [adminData, setAdminData] = useState<any>(null),
    [adminTab, setAdminTab] = useState("proofs"),
    [category, setCategory] = useState("general"),
    [draft, setDraft] = useState(""),
    [raceWindow, setRaceWindow] = useState<number | null>(null),
    [historyDay, setHistoryDay] = useState("");
  const dialog = useRef<HTMLDialogElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const [proofUrl, setProofUrl] = useState("");
  const cfg = data?.config || defaultConfig,
    clans = cfg.clans,
    p = data?.participant,
    stats = data?.stats;
  const clock = now + skew;
  const live = data?.schedules.find(
    (s: any) => clock >= s.startAt && clock < s.endAt,
  );
  const next = data?.schedules.find((s: any) => clock < s.startAt);
  const reset = data
    ? Date.parse(data.day + "T00:00:00+09:00") + 86400000
    : clock;
  const until = Math.max(
    0,
    Math.floor(((live?.endAt || next?.startAt || reset) - clock) / 1000),
  );
  const countDown = [
    Math.floor(until / 3600),
    Math.floor(until / 60) % 60,
    until % 60,
  ]
    .map((n) => String(n).padStart(2, "0"))
    .join(" : ");
  const selectedSchedule = stats?.schedules.find(
    (s: any) =>
      s.id ===
      (raceWindow ||
        live?.id ||
        data?.schedules.filter((s: any) => s.endAt <= clock).at(-1)?.id),
  );
  const raceStats =
    selectedSchedule?.clans ||
    clans.map((c: any) => ({ clan: c.id, verified: 0, eligible: 0 }));
  const leaders = leader(raceStats);
  const selectedCountry = cn(country);
  const myClan = clans.find((c: any) => c.id === p?.clan);
  async function refresh() {
    try {
      const r = await apiFetch("/api/coven", { cache: "no-store" });
      const j: any = await r.json();
      if (!r.ok) throw Error(j.error);
      setData(j);
      setSkew(j.serverNow - Date.now());
      setError("");
    } catch (e) {
      setError((e as Error).message);
    }
  }
  async function post(body: any) {
    const r = await apiFetch("/api/coven", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    const j: any = await r.json();
    if (!r.ok) throw Error(j.error);
    return j;
  }
  useEffect(() => {
    refresh();
    const t = setInterval(refresh, 20000),
      s = setInterval(() => setNow(Date.now()), 1000);
    try {
      const saved = JSON.parse(
        localStorage.getItem("coven-onboarding") || "null",
      );
      if (saved) {
        setCountry(saved.country || "");
        setAnswers(saved.answers || []);
        setQ(saved.q || 0);
        setStep(saved.step || "country");
        setResult(saved.result || null);
        setTied(saved.tied || []);
      }
    } catch {}
    return () => {
      clearInterval(t);
      clearInterval(s);
    };
  }, []);
  useEffect(() => {
    if (onboard) {
      localStorage.setItem(
        "coven-onboarding",
        JSON.stringify({ country, answers, q, step, result, tied }),
      );
      if (!dialog.current?.open) dialog.current?.showModal();
    } else dialog.current?.close();
  }, [onboard, country, answers, q, step, result, tied]);
  useEffect(() => {
    const fromHash = location.hash.slice(1);
    if (
      nav.some((n) => n[0] === fromHash) ||
      ["dashboard", "admin"].includes(fromHash)
    )
      setPage(fromHash);
    const h = () => setPage(location.hash.slice(1) || "home");
    addEventListener("hashchange", h);
    return () => removeEventListener("hashchange", h);
  }, []);
  function go(id: string) {
    setPage(id);
    setMobile(false);
    location.hash = id;
    window.scrollTo({ top: 0, behavior: "smooth" });
  }
  function join() {
    if (p) {
      go("dashboard");
      return;
    }
    setOnboard(true);
    setQuizError("");
  }
  async function finish(tie?: string) {
    setBusy(true);
    setQuizError("");
    try {
      const r = await post({ action: "quiz", country, answers, tie });
      if (r.tied?.length) {
        setTied(r.tied);
        setStep("tie");
      } else {
        setResult(r);
        setStep("result");
      }
    } catch (e) {
      setQuizError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function accept() {
    setBusy(true);
    try {
      await post({ action: "confirm", resultId: result.resultId });
      await refresh();
      localStorage.removeItem("coven-onboarding");
      setOnboard(false);
      go("dashboard");
      setNotice("Your coven is confirmed. Welcome to the gathering.");
    } catch (e) {
      setQuizError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function loadAdmin() {
    setBusy(true);
    try {
      const a = await post({ action: "admin" });
      setAdminData(a);
      setDraft(JSON.stringify(a.config[category], null, 2));
    } catch (e) {
      setNotice((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => {
    if (page === "admin" && data?.admin) loadAdmin();
  }, [page, data?.admin]);
  async function submitProof(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      const form = new FormData();
      form.set("url", proofUrl);
      if (fileRef.current?.files?.[0])
        form.set("file", fileRef.current.files[0]);
      const r = await apiFetch("/api/coven", { method: "POST", body: form });
      const j: any = await r.json();
      if (!r.ok) throw Error(j.error);
      setNotice(
        j.status === "DUPLICATE"
          ? "This proof has already been submitted and is marked duplicate."
          : "Proof received. It will count after an administrator verifies it.",
      );
      setProofUrl("");
      if (fileRef.current) fileRef.current.value = "";
      await refresh();
    } catch (e) {
      setNotice((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  function sectionHeader(kicker: string, title: string, description?: string) {
    return (
      <div className="section-heading">
        <div>
          <div className="eyebrow">{kicker}</div>
          <h2>{title}</h2>
          {description && <p>{description}</p>}
        </div>
      </div>
    );
  }
  function clanCards() {
    return (
      <div className="clan-grid">
        {clans.map((c: any, i: number) => (
          <article
            className={"clan-card " + c.id}
            key={c.id}
            style={{ "--clan": c.color } as any}
          >
            <span className="roman">0{i + 1}</span>
            <Crest clan={c.id} />
            <div className="eyebrow">THE {c.name} COVEN</div>
            <h3>{c.motto}</h3>
            <p>{c.traits}</p>
            <div className="clan-bottom">
              <span>
                <b>
                  {stats?.clans.find((x: any) => x.clan === c.id)?.members ??
                    "—"}
                </b>{" "}
                ENGENEs
              </span>
              <button
                className="icon-link"
                onClick={join}
                aria-label={"Discover " + c.name}
              >
                <ArrowUpRight size={19} />
              </button>
            </div>
          </article>
        ))}
      </div>
    );
  }
  function scheduleCards() {
    return (
      <div className="schedule-grid">
        {data?.schedules.map((s: any, i: number) => {
          const status =
            clock < s.startAt ? "UPCOMING" : clock < s.endAt ? "LIVE" : "ENDED";
          return (
            <div
              className={"schedule " + (status === "LIVE" ? "is-live" : "")}
              key={s.id}
            >
              <div className="row">
                <span className="eyebrow">WINDOW 0{i + 1}</span>
                <span className={"status " + status.toLowerCase()}>
                  {status === "LIVE" && <i />}
                  {status}
                </span>
              </div>
              <h3>{s.name}</h3>
              <div className="schedule-time">
                {s.start} <span>—</span> {s.end} <small>KST</small>
              </div>
              <p>
                {time(
                  s.startAt,
                  p?.timezone ||
                    Intl.DateTimeFormat().resolvedOptions().timeZone,
                )}{" "}
                –{" "}
                {time(
                  s.endAt,
                  p?.timezone ||
                    Intl.DateTimeFormat().resolvedOptions().timeZone,
                )}{" "}
                <small>LOCAL</small>
              </p>
            </div>
          );
        }) || <div className="empty">Loading race windows…</div>}
      </div>
    );
  }
  function leaderboard() {
    return (
      <div className="race-panel">
        <div className="row">
          <div>
            <div className="eyebrow">
              {selectedSchedule
                ? selectedSchedule.name + " · SCHEDULE RANKING"
                : "WAITING FOR THE FIRST WINDOW"}
            </div>
            <h3>
              {!leaders.length
                ? "The covens are waiting."
                : leaders.length > 1
                  ? "A tie under the moon."
                  : clans.find((c: any) => c.id === leaders[0])?.name +
                    " leads the night."}
            </h3>
            <p>
              {!leaders.length
                ? "No verified check-ins yet. The next chapter begins with you."
                : "Fan-made participation only. Every verified effort counts."}
            </p>
          </div>
          <Moon className="panel-moon" size={36} strokeWidth={1} />
        </div>
        <div className="race-table">
          {raceStats.map((s: any, i: number) => {
            const c = clans.find((c: any) => c.id === s.clan);
            return (
              <div
                className="race-row"
                key={c.id}
                style={{ "--clan": c.color } as any}
              >
                <span className="rank">0{i + 1}</span>
                <Crest clan={c.id} small />
                <div className="race-name">
                  <strong>{c.name}</strong>
                  <span>
                    {s.verified} verified / {s.eligible} eligible
                  </span>
                </div>
                <div className="bar">
                  <div
                    style={{ width: percentage(s.verified, s.eligible) + "%" }}
                  />
                </div>
                <strong className="percent">
                  {percentage(s.verified, s.eligible).toFixed(
                    s.verified ? 1 : 0,
                  )}
                  <small>%</small>
                </strong>
              </div>
            );
          })}
        </div>
        <div className="panel-foot">
          <ShieldCheck size={14} /> Only verified proofs count{" "}
          <span>Denominators locked at schedule start</span>
        </div>
      </div>
    );
  }
  function world() {
    const people = stats?.people || [];
    const ids = [...new Set(people.map((x: any) => x.country))] as string[];
    const regions = [...new Set(people.map((x: any) => x.region))] as string[];
    return (
      <>
        <div className="world-stats">
          <div>
            <Globe2 />
            <strong>{stats?.total ?? "—"}</strong>
            <span>REGISTERED ENGENEs</span>
          </div>
          <div>
            <Compass />
            <strong>{data ? ids.length : "—"}</strong>
            <span>COUNTRIES REPRESENTED</span>
          </div>
          <div>
            <Users />
            <strong>{data ? regions.length : "—"}</strong>
            <span>VOTING REGIONS</span>
          </div>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>COUNTRY ROLL CALL</th>
                {clans.map((c: any) => (
                  <th key={c.id}>{c.name}</th>
                ))}
                <th>TOTAL</th>
              </tr>
            </thead>
            <tbody>
              {ids.map((id) => (
                <tr key={id}>
                  <td>
                    {cn(id)?.flag} {cn(id)?.name}
                  </td>
                  {clans.map((c: any) => (
                    <td key={c.id}>
                      {people
                        .filter((x: any) => x.country === id && x.clan === c.id)
                        .reduce((n: number, x: any) => n + x.n, 0)}
                    </td>
                  ))}
                  <td>
                    {people
                      .filter((x: any) => x.country === id)
                      .reduce((n: number, x: any) => n + x.n, 0)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {ids.length === 0 && (
            <div className="empty">
              <Globe2 size={28} strokeWidth={1} />
              <h3>A world waiting to gather.</h3>
              <p>
                Countries appear after an ENGENE completes the sorting and
                accepts their coven.
              </p>
              <button className="text-link" onClick={join}>
                Be the first to join <ArrowRight size={15} />
              </button>
            </div>
          )}
        </div>
        {regions.length > 0 && (
          <>
            <h3 className="subhead">Regional gathering</h3>
            <div className="region-grid">
              {regions.map((r) => {
                const rows = people.filter((x: any) => x.region === r);
                const zone = cn(rows[0].country)?.timezone || "Asia/Seoul";
                const activity = stats.activity.filter(
                  (a: any) => a.region === r,
                );
                const verified = activity.reduce(
                    (n: number, a: any) => n + a.verified,
                    0,
                  ),
                  eligible = activity.reduce(
                    (n: number, a: any) => n + a.eligible,
                    0,
                  );
                return (
                  <div className="panel" key={r}>
                    <div className="eyebrow">{cfg.regions[r] || r}</div>
                    <h3>
                      {rows.reduce((n: number, x: any) => n + x.n, 0)} ENGENEs
                    </h3>
                    <p>
                      {verified} verified / {eligible} eligible ·{" "}
                      {percentage(verified, eligible).toFixed(1)}%
                    </p>
                    <p>
                      {stats.activityWindow || "No window started"} ·
                      participation
                    </p>
                    <p>
                      {time(clock, zone)} · {zone} (coordination reference)
                    </p>
                    <p>
                      Next:{" "}
                      {next
                        ? time(next.startAt, zone) +
                          " local · " +
                          next.start +
                          " KST"
                        : "Tomorrow, " + cfg.schedules[0].start + " KST"}
                    </p>
                  </div>
                );
              })}
            </div>
          </>
        )}
      </>
    );
  }
  return (
    <>
      <a className="skip" href="#main">
        Skip to content
      </a>
      <header className="header">
        <a className="brand" href="#home" onClick={() => go("home")}>
          <Moon size={24} strokeWidth={1} />
          <span>
            THE COVEN<small>G A T H E R I N G</small>
          </span>
        </a>
        <nav className={mobile ? "open" : ""} aria-label="Main navigation">
          {nav.map(([id, label]) => (
            <a
              key={id}
              href={"#" + id}
              onClick={() => go(id)}
              className={page === id ? "active" : ""}
            >
              {label}
            </a>
          ))}
        </nav>
        <button className="header-join" onClick={join}>
          {p ? "MY COVEN" : "ENTER THE GATHERING"}
          <ArrowUpRight size={14} />
        </button>
        <button
          className="menu"
          onClick={() => setMobile(!mobile)}
          aria-label="Toggle navigation"
          aria-expanded={mobile}
        >
          {mobile ? <X /> : <Menu />}
        </button>
      </header>
      <main id="main">
        {error && (
          <div className="error-banner" role="alert">
            <AlertCircle size={16} />
            {error}
            <button onClick={refresh}>Retry</button>
          </div>
        )}
        {cfg.banners.enabled && (
          <div className="announcement">{cfg.banners.text}</div>
        )}
        {notice && (
          <div className="toast" role="status">
            {notice}
            <button onClick={() => setNotice("")} aria-label="Dismiss">
              <X size={16} />
            </button>
          </div>
        )}
        {page === "home" && (
          <>
            <section className="hero">
              <div className="moon-orbit" aria-hidden="true">
                <div className="moon-disc" />
                <span className="orbit-mark">✦</span>
              </div>
              <Forest />
              <div className="hero-inner">
                <div className="eyebrow hero-kicker">
                  <span /> ENGENE FAN PROJECT <b> / </b> MAMA 2026
                </div>
                <h1>
                  {cfg.general.title === defaultConfig.general.title ? (
                    <>
                      The Coven
                      <br />
                      <em>Gathering.</em>
                    </>
                  ) : (
                    cfg.general.title
                  )}
                </h1>
                <div className="hero-rule" />
                <p className="hero-tag">
                  24 HOURS <span>✦</span> 4 COVENS <span>✦</span> 1 FANDOM
                </p>
                <p className="hero-copy">{cfg.content.intro}</p>
                <div className="hero-actions">
                  <button className="primary" onClick={join}>
                    {p ? "ENTER MY COVEN" : "BEGIN THE SORTING"}
                    <ArrowUpRight size={17} />
                  </button>
                  <button className="secondary" onClick={() => go("race")}>
                    VIEW LIVE RACE
                    <ArrowRight size={16} />
                  </button>
                </div>
                <div className="hero-note">
                  <ShieldCheck size={13} /> Built by ENGENEs. United for
                  ENHYPEN.
                </div>
              </div>
              <div className="hero-side">THE NIGHT BRINGS US TOGETHER</div>
              <div className="time-strip">
                <div>
                  <span className="eyebrow">
                    <i className="live-dot" /> LIVE KST CLOCK
                  </span>
                  <strong>
                    {time(clock, "Asia/Seoul", true)}
                    <small>KST</small>
                  </strong>
                </div>
                <div>
                  <span className="eyebrow">CURRENT RACE DAY</span>
                  <strong className="date">{data?.day || "—"}</strong>
                </div>
                <div>
                  <span className="eyebrow">
                    {live
                      ? "WINDOW CLOSES IN"
                      : next
                        ? "NEXT WINDOW IN"
                        : "NEW RACE IN"}
                  </span>
                  <strong>{countDown}</strong>
                </div>
                <div className="strip-status">
                  <span className={"status " + (live ? "live" : "")}>
                    {live ? (
                      <>
                        <i /> GATHERING LIVE
                      </>
                    ) : (
                      "THE NIGHT CONTINUES"
                    )}
                  </span>
                  <span>
                    {live
                      ? live.name
                      : next
                        ? next.start + " – " + next.end + " KST"
                        : "Resets at 00:00 KST"}
                  </span>
                </div>
              </div>
            </section>
            <div className="content">
              <section className="intro-row">
                <div>
                  <div className="eyebrow">
                    ONE FANDOM. FOUR WAYS TO BELONG.
                  </div>
                  <h2>Find your coven.</h2>
                </div>
                <p>
                  Some lead. Some connect. Some quietly keep going.
                  <br />
                  Every kind of ENGENE belongs here.
                </p>
                <button className="text-link" onClick={() => go("clans")}>
                  Meet the covens
                  <ArrowUpRight size={16} />
                </button>
              </section>
              {clanCards()}
              <section className="section">
                <div className="section-heading">
                  <div>
                    <div className="eyebrow">
                      THE COVEN RACE <span className="tiny-dot" />{" "}
                      {data?.day || "KST"}
                    </div>
                    <h2>A gathering, built together.</h2>
                  </div>
                  <button className="text-link" onClick={() => go("race")}>
                    View the race
                    <ArrowUpRight size={16} />
                  </button>
                </div>
                {leaderboard()}
              </section>
              <section className="section">
                {sectionHeader(
                  "TODAY’S OPERATION",
                  "Four moments. One purpose.",
                  "Four synchronized windows. All race times use Asia/Seoul.",
                )}
                {scheduleCards()}
              </section>
              <section className="section">
                {sectionHeader(
                  "ENGENEs AROUND THE WORLD",
                  "Different places. The same moon.",
                )}
                {world()}
              </section>
              <section className="join-band">
                <Crest small />
                <div>
                  <div className="eyebrow">YOUR PLACE IS WAITING</div>
                  <h2>The night has a coven for you.</h2>
                  <p>Eight questions. Your way of supporting ENHYPEN.</p>
                </div>
                <button className="primary" onClick={join}>
                  {p ? "MY COVEN" : "BEGIN THE SORTING"}
                  <ArrowUpRight size={16} />
                </button>
              </section>
            </div>
          </>
        )}
        {page !== "home" && (
          <div className="content subpage">
            <div className="breadcrumb">
              THE COVEN GATHERING <span>/</span>{" "}
              {page === "dashboard" ? "MY COVEN" : page.toUpperCase()}
            </div>
            {page === "clans" && (
              <>
                {sectionHeader(
                  "FOUR COVENS. ONE FANDOM.",
                  "Where will the night lead you?",
                  "An entertainment-based ENGENE sorting experience. No right answers, no psychological assessments.",
                )}
                {clanCards()}
                <div className="clan-stories">
                  {clans.map((c: any) => (
                    <article className="panel" key={c.id}>
                      <Crest small clan={c.id} />
                      <div>
                        <h3>{c.name}</h3>
                        <p>{c.description}</p>
                      </div>
                    </article>
                  ))}
                </div>
                <div className="center">
                  <button className="primary" onClick={join}>
                    FIND MY COVEN
                    <ArrowUpRight size={16} />
                  </button>
                </div>
              </>
            )}
            {page === "race" && (
              <>
                {sectionHeader(
                  "LIVE CLAN RACE · " + (data?.day || "KST"),
                  "Every check-in counts.",
                  "Verified check-ins ÷ eligible members × 100. These are fan-made participation statistics, never official voting results.",
                )}
                <div className="window-tabs">
                  {data?.schedules.map((s: any) => (
                    <button
                      key={s.id}
                      className={
                        selectedSchedule?.id === s.id ? "selected" : ""
                      }
                      onClick={() => setRaceWindow(s.id)}
                    >
                      {s.name}
                      <small>
                        {s.start} – {s.end} KST
                      </small>
                    </button>
                  ))}
                </div>
                {leaderboard()}
                {live && (
                  <div className="all-out">
                    <span className="eyebrow">ALL OUT · {live.name}</span>
                    <h3>Every unchecked coven member — go now.</h3>
                    <p>
                      {data.stats.schedules
                        .find((s: any) => s.id === live.id)
                        ?.clans.reduce(
                          (n: number, c: any) =>
                            n + Math.max(0, c.eligible - c.verified),
                          0,
                        )}{" "}
                      eligible members still without a verified check-in this
                      window.
                    </p>
                    <button className="primary" onClick={join}>
                      GO TO CHECK-IN
                      <ArrowRight size={16} />
                    </button>
                  </div>
                )}
                <section className="section">{scheduleCards()}</section>
                <button className="secondary" onClick={join}>
                  OPEN MY DASHBOARD
                  <ArrowUpRight size={16} />
                </button>
              </>
            )}
            {page === "worldwide" && (
              <>
                {sectionHeader(
                  "ENGENEs AROUND THE WORLD",
                  "The fandom, without borders.",
                  "Only confirmed participants appear. Countries and voting regions use aggregate counts.",
                )}
                {world()}
              </>
            )}
            {page === "history" && (
              <>
                {sectionHeader(
                  "RACE HISTORY",
                  "The nights we shared.",
                  "Final snapshots are preserved at the KST day boundary. New members never change past results.",
                )}
                {!data?.history.length ? (
                  <div className="empty tall">
                    <Moon size={38} strokeWidth={1} />
                    <h3>Our story is just beginning.</h3>
                    <p>
                      Completed race days will appear here after the first
                      midnight in Seoul.
                    </p>
                    <button className="text-link" onClick={() => go("race")}>
                      Today’s race
                      <ArrowRight size={15} />
                    </button>
                  </div>
                ) : (
                  <>
                    {data.history.map((h: any) => (
                      <details className="history-item" key={h.day}>
                        <summary>
                          <span>{h.day}</span>
                          <span>
                            {h.total} registered · {h.verified} verified
                          </span>
                          <ChevronRight size={18} />
                        </summary>
                        <div className="history-body">
                          <p>
                            Daily percentage uses all eligible schedule slots.
                            Schedule results below preserve each denominator.
                          </p>
                          <div className="history-totals">
                            {h.clans.map((c: any) => (
                              <div key={c.clan}>
                                <strong>{c.clan.toUpperCase()}</strong>
                                <h3>
                                  {percentage(c.verified, c.eligible).toFixed(
                                    1,
                                  )}
                                  %
                                </h3>
                                <p>
                                  {c.verified} / {c.eligible} eligible slots
                                </p>
                              </div>
                            ))}
                          </div>
                          <div className="table-wrap">
                            <table>
                              <thead>
                                <tr>
                                  <th>SCHEDULE</th>
                                  {clans.map((c: any) => (
                                    <th key={c.id}>{c.name}</th>
                                  ))}
                                </tr>
                              </thead>
                              <tbody>
                                {h.schedules.map((s: any) => (
                                  <tr key={s.id}>
                                    <td>{s.name}</td>
                                    {s.clans.map((c: any) => (
                                      <td key={c.clan}>
                                        {c.verified}/{c.eligible} ·{" "}
                                        {percentage(
                                          c.verified,
                                          c.eligible,
                                        ).toFixed(1)}
                                        %
                                      </td>
                                    ))}
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                          <p>
                            {h.people
                              .map(
                                (p: any) =>
                                  `${cn(p.country)?.name} · ${p.region} · ${p.clan}: ${p.n}`,
                              )
                              .join(" / ") ||
                              "No registered participants on this day."}
                          </p>
                        </div>
                      </details>
                    ))}
                  </>
                )}
              </>
            )}
            {page === "guide" && (
              <>
                {sectionHeader(
                  "OFFICIAL INFORMATION · SEPARATE FROM THE RACE",
                  "MAMA voting guide.",
                  "The gathering encourages participation. Actual votes must be cast on the official platform under official rules.",
                )}
                <div className="guide-banner">
                  <ShieldCheck size={28} />
                  <div>
                    <h3>Official rules always come first.</h3>
                    <p>
                      Check source links and current announcements before
                      voting. A check-in here is not a vote.
                    </p>
                  </div>
                </div>
                {cfg.guide.sources.length ? (
                  Object.entries(cfg.guide)
                    .filter(([k]) => k !== "sources")
                    .map(([k, v]) => (
                      <div className="guide-item" key={k}>
                        <div className="eyebrow">{k.toUpperCase()}</div>
                        <p>{String(v) || "Not yet published."}</p>
                      </div>
                    ))
                ) : (
                  <div className="empty tall">
                    <h3>Waiting for verified official information.</h3>
                    <p>
                      The platform, period, eligibility, limits and instructions
                      will be added only with verified source links.
                    </p>
                    <span className="status">NO RULES INVENTED</span>
                  </div>
                )}
                {cfg.guide.sources.map((url: string) => (
                  <a
                    className="source-link"
                    key={url}
                    href={url}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {url}
                    <ExternalLink size={15} />
                  </a>
                ))}
              </>
            )}
            {page === "rules" && (
              <>
                {sectionHeader(
                  "THE COVEN CODE",
                  "A fair race. A united fandom.",
                )}
                <ol className="rules-list">
                  {rules.map((r, i) => (
                    <li key={r}>
                      <span>{String(i + 1).padStart(2, "0")}</span>
                      {r}
                    </li>
                  ))}
                </ol>
                <div className="panel privacy">
                  <h3>Your participation, your privacy.</h3>
                  <p>
                    Only aggregate statistics are public. Names, emails, precise
                    locations, IP addresses, individual histories, proof URLs
                    and moderation details are never shown on public dashboards.
                    Uploaded proofs can be seen only by their owner and the
                    administrator.
                  </p>
                  <p>
                    Your participation is remembered in this browser with a
                    private cookie. Clearing cookies or switching browser
                    creates a separate identity; there is no account recovery.
                    Your country and primary timezone help coordinate schedules.
                    Multi-timezone countries use a primary zone; your dashboard
                    also shows your device’s time for convenience.
                  </p>
                </div>
              </>
            )}
            {page === "dashboard" &&
              (!p ? (
                <div className="empty tall">
                  <Crest />
                  <h2>Your coven is waiting.</h2>
                  <p>
                    Choose your country, complete the quiz, then accept your
                    clan.
                  </p>
                  <button className="primary" onClick={join}>
                    BEGIN THE SORTING
                    <ArrowRight size={16} />
                  </button>
                </div>
              ) : (
                <>
                  <div className="dashboard-head">
                    <Crest clan={p.clan} />
                    <div>
                      <div className="eyebrow">THIS IS YOUR COVEN</div>
                      <h2>{myClan?.name}</h2>
                      <p>
                        {cn(p.country)?.flag} {cn(p.country)?.name}{" "}
                        <span>·</span> {p.region}
                      </p>
                      <span className="lock">
                        <LockKeyhole size={12} /> CLAN LOCKED
                      </span>
                    </div>
                  </div>
                  <div className="dashboard-times">
                    <div>
                      <span className="eyebrow">YOUR COUNTRY TIME</span>
                      <strong>{time(clock, p.timezone, true)}</strong>
                      <p>
                        {p.timezone} · {offset(p.timezone, clock)}
                      </p>
                    </div>
                    <div>
                      <span className="eyebrow">SEOUL · RACE TIME</span>
                      <strong>{time(clock, "Asia/Seoul", true)}</strong>
                      <p>Asia/Seoul · UTC+9</p>
                    </div>
                    <div>
                      <span className="eyebrow">YOUR DEVICE TIME</span>
                      <strong>
                        {time(
                          clock,
                          Intl.DateTimeFormat().resolvedOptions().timeZone,
                          true,
                        )}
                      </strong>
                      <p>{Intl.DateTimeFormat().resolvedOptions().timeZone}</p>
                    </div>
                  </div>
                  <section className="section">
                    {sectionHeader(
                      "YOUR DAILY GATHERING",
                      "Show up for your coven.",
                    )}
                    {scheduleCards()}
                  </section>
                  <section className="proof-panel">
                    <div>
                      <div className="eyebrow">COVEN MEMBER CHECK-IN</div>
                      <h2>
                        {live
                          ? "The window is open."
                          : "Meet us at the next window."}
                      </h2>
                      <p>
                        {live
                          ? live.start + " – " + live.end + " KST"
                          : next
                            ? "Next: " +
                              next.start +
                              " KST · " +
                              time(next.startAt, p.timezone) +
                              " your country time"
                            : "Today’s windows have ended. See you tomorrow."}
                      </p>
                      <p>
                        Submit your voting or MV check-in proof. Remove any
                        personal details before uploading. Proofs remain
                        private.
                      </p>
                      <span className="lock">
                        <ShieldCheck size={14} /> Admin verification required
                      </span>
                    </div>
                    {live &&
                    p.created_at < live.startAt &&
                    !data.myProofs.some(
                      (x: any) => x.schedule_id === live.id,
                    ) ? (
                      <form onSubmit={submitProof}>
                        <label htmlFor="url">Proof URL</label>
                        <input
                          id="url"
                          type="url"
                          placeholder="https://…"
                          value={proofUrl}
                          onChange={(e) => setProofUrl(e.target.value)}
                        />
                        <div className="or">OR UPLOAD AN IMAGE</div>
                        <label className="upload" htmlFor="file">
                          <Upload size={20} />
                          <span>
                            Choose a proof image
                            <small>JPG, PNG, WEBP · up to 5 MB</small>
                          </span>
                          <input
                            ref={fileRef}
                            id="file"
                            type="file"
                            accept="image/jpeg,image/png,image/webp"
                          />
                        </label>
                        <button className="primary" disabled={busy}>
                          {busy ? "SUBMITTING…" : "SUBMIT PROOF"}
                          <ArrowUpRight size={16} />
                        </button>
                      </form>
                    ) : (
                      <div className="proof-closed">
                        <Clock3 size={30} strokeWidth={1} />
                        <h3>
                          {live &&
                          data.myProofs.find(
                            (x: any) => x.schedule_id === live.id,
                          )
                            ? data.myProofs.find(
                                (x: any) => x.schedule_id === live.id,
                              ).status
                            : live
                              ? "You’re in for the next window."
                              : "A little patience, ENGENE."}
                        </h3>
                        <p>
                          {live && p.created_at >= live.startAt
                            ? "This window’s eligible members were locked before you joined."
                            : "One submission per window. Only verified proofs enter the race."}
                        </p>
                      </div>
                    )}
                  </section>
                  <div className="my-statuses">
                    {data.schedules.map((s: any) => (
                      <div key={s.id}>
                        <span>{s.name}</span>
                        <span className="status">
                          {data.myProofs.find(
                            (x: any) => x.schedule_id === s.id,
                          )?.status || "NOT SUBMITTED"}
                        </span>
                      </div>
                    ))}
                  </div>
                </>
              ))}
            {page === "admin" &&
              (data?.admin ? (
                <>
                  {sectionHeader(
                    "KEEPERS OF THE GATHERING",
                    "Admin console.",
                    "Proofs and moderation details are private. Archived days are immutable.",
                  )}
                  <button
                    className="text-link"
                    onClick={() => {
                      clearAdmin();
                      setAdminData(null);
                      refresh();
                    }}
                  >
                    Sign out <LockKeyhole size={12} />
                  </button>
                  <div className="window-tabs">
                    {["proofs", "coordination", "configuration"].map((t) => (
                      <button
                        key={t}
                        className={adminTab === t ? "selected" : ""}
                        onClick={() => setAdminTab(t)}
                      >
                        {t}
                      </button>
                    ))}
                  </div>
                  {!adminData ? (
                    <div className="empty">Loading administration…</div>
                  ) : adminTab === "proofs" ? (
                    <>
                      <p className="muted">
                        {cfg.admin.reviewNote} Pending proofs at day close never
                        count retroactively.
                      </p>
                      {!adminData.proofs.length ? (
                        <div className="empty tall">
                          <ShieldCheck size={32} />
                          <h3>No proofs to review.</h3>
                          <p>
                            Real submissions will appear here during live race
                            windows.
                          </p>
                        </div>
                      ) : (
                        adminData.proofs.map((proof: any) => (
                          <div className="review" key={proof.id}>
                            <div>
                              <span className="eyebrow">
                                {proof.race_day_id} · WINDOW {proof.schedule_id}{" "}
                                · {proof.clan}
                              </span>
                              <h3>{proof.status}</h3>
                              {proof.object_key ? (
                                <button
                                  className="text-link"
                                  onClick={() =>
                                    openPrivateProof(proof.id).catch((e) =>
                                      setNotice(e.message),
                                    )
                                  }
                                >
                                  View private image
                                  <ExternalLink size={14} />
                                </button>
                              ) : (
                                <a
                                  className="text-link"
                                  href={proof.proof_url}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                >
                                  Open submitted URL
                                  <ExternalLink size={14} />
                                </a>
                              )}
                            </div>
                            {proof.status === "PENDING" &&
                              proof.race_day_id === data.day && (
                                <div className="review-actions">
                                  {["VERIFIED", "REJECTED", "DUPLICATE"].map(
                                    (status) => (
                                      <button
                                        className="secondary"
                                        disabled={busy}
                                        key={status}
                                        onClick={async () => {
                                          setBusy(true);
                                          try {
                                            await post({
                                              action: "review",
                                              id: proof.id,
                                              status,
                                            });
                                            await loadAdmin();
                                            await refresh();
                                          } catch (e) {
                                            setNotice((e as Error).message);
                                          } finally {
                                            setBusy(false);
                                          }
                                        }}
                                      >
                                        {status}
                                      </button>
                                    ),
                                  )}
                                </div>
                              )}
                          </div>
                        ))
                      )}
                    </>
                  ) : adminTab === "coordination" ? (
                    <>
                      <p className="muted">
                        Fan coordination suggestions only. The recommended next
                        available window is not an official MAMA rule.
                      </p>
                      <div className="table-wrap">
                        <table>
                          <thead>
                            <tr>
                              <th>COUNTRY / REGION</th>
                              <th>MEMBERS</th>
                              <th>VERIFIED / ELIGIBLE</th>
                              <th>UNCHECKED</th>
                              <th>COUNTRY TIME</th>
                              <th>NEXT WINDOW</th>
                            </tr>
                          </thead>
                          <tbody>
                            {[
                              ...new Set(
                                adminData.coordination.map(
                                  (r: any) => r.country,
                                ),
                              ),
                            ].map((id: any) => {
                              const rows = adminData.coordination.filter(
                                (r: any) => r.country === id,
                              );
                              const zone = cn(id)?.timezone || "Asia/Seoul";
                              const a = data.stats.activity.filter(
                                (x: any) => x.country === id,
                              );
                              const v = a.reduce(
                                  (n: number, x: any) => n + x.verified,
                                  0,
                                ),
                                el = a.reduce(
                                  (n: number, x: any) => n + x.eligible,
                                  0,
                                );
                              return (
                                <tr key={id}>
                                  <td>
                                    {cn(id)?.name}
                                    <small>{rows[0].region}</small>
                                  </td>
                                  <td>
                                    {rows.reduce(
                                      (n: number, r: any) => n + r.members,
                                      0,
                                    )}
                                  </td>
                                  <td>
                                    {v} / {el}
                                    <small>
                                      {data.stats.activityWindow ||
                                        "No window started"}
                                    </small>
                                  </td>
                                  <td>{Math.max(0, el - v)}</td>
                                  <td>
                                    {time(clock, zone)}
                                    <small>{time(clock)} KST</small>
                                  </td>
                                  <td>
                                    {next
                                      ? next.start +
                                        " KST / " +
                                        time(next.startAt, zone) +
                                        " local"
                                      : "Tomorrow " +
                                        cfg.schedules[0].start +
                                        " KST"}
                                  </td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>
                        {!adminData.coordination.length && (
                          <div className="empty">No active regions yet.</div>
                        )}
                      </div>
                    </>
                  ) : (
                    <div className="config-layout">
                      <aside>
                        {Object.keys(defaultConfig).map((k) => (
                          <button
                            key={k}
                            className={category === k ? "selected" : ""}
                            onClick={() => {
                              setCategory(k);
                              setDraft(
                                JSON.stringify(adminData.config[k], null, 2),
                              );
                            }}
                          >
                            {
                              (
                                {
                                  general: "General",
                                  clans: "Clans",
                                  quiz: "Quiz",
                                  questions: "Quiz questions",
                                  scoring: "Quiz scoring",
                                  schedules: "Schedules",
                                  countries: "Countries",
                                  regions: "Voting regions",
                                  guide: "MAMA guide",
                                  content: "Website content",
                                  banners: "Banners",
                                  admin: "Admin settings",
                                } as any
                              )[k]
                            }
                          </button>
                        ))}
                      </aside>
                      <form
                        onSubmit={async (e) => {
                          e.preventDefault();
                          setBusy(true);
                          try {
                            const updated = {
                              ...adminData.config,
                              [category]: JSON.parse(draft),
                            };
                            await post({ action: "config", config: updated });
                            await loadAdmin();
                            await refresh();
                            setNotice(
                              "Configuration saved. Schedule changes apply from the next KST race day; historical records stay unchanged.",
                            );
                          } catch (e) {
                            setNotice((e as Error).message);
                          } finally {
                            setBusy(false);
                          }
                        }}
                      >
                        <h3>{category.toUpperCase()}</h3>
                        <p className="muted">
                          Edit the structured configuration below. Stable IDs
                          must be preserved. Country overrides use ISO alpha-2
                          keys with region and optional timezone. Existing
                          participant assignments remain locked.
                        </p>
                        <label htmlFor="config">Configuration JSON</label>
                        <textarea
                          id="config"
                          spellCheck={false}
                          rows={20}
                          value={draft}
                          onChange={(e) => setDraft(e.target.value)}
                        />
                        <button className="primary" disabled={busy}>
                          {busy ? "SAVING…" : "SAVE CONFIGURATION"}
                          <Check size={16} />
                        </button>
                      </form>
                    </div>
                  )}
                </>
              ) : (
                <form
                  className="admin-login panel"
                  onSubmit={async (e) => {
                    e.preventDefault();
                    setBusy(true);
                    try {
                      setAdmin(adminUser, adminPassword);
                      await post({ action: "admin" });
                      setAdminPassword("");
                      await refresh();
                    } catch (e) {
                      clearAdmin();
                      setNotice((e as Error).message);
                    } finally {
                      setBusy(false);
                    }
                  }}
                >
                  <LockKeyhole />
                  <h3>Keepers of the gathering.</h3>
                  <p>Sign in to review proofs and manage configuration.</p>
                  <label htmlFor="admin-user">Username</label>
                  <input
                    id="admin-user"
                    autoComplete="username"
                    value={adminUser}
                    onChange={(e) => setAdminUser(e.target.value)}
                    required
                  />
                  <label htmlFor="admin-password">Password</label>
                  <input
                    id="admin-password"
                    type="password"
                    autoComplete="current-password"
                    value={adminPassword}
                    onChange={(e) => setAdminPassword(e.target.value)}
                    required
                  />
                  <button className="primary full" disabled={busy}>
                    SIGN IN
                    <ArrowRight size={16} />
                  </button>
                </form>
              ))}
          </div>
        )}
      </main>
      <footer>
        <div className="footer-top">
          <a className="brand" href="#home" onClick={() => go("home")}>
            <Moon size={22} strokeWidth={1} />
            <span>
              THE COVEN<small>G A T H E R I N G</small>
            </span>
          </a>
          <p>
            Many ways to show up.
            <br />
            One reason to stay.
          </p>
          <div>
            <button className="text-link" onClick={() => go("rules")}>
              Rules & privacy
              <ArrowUpRight size={13} />
            </button>
            <button className="text-link" onClick={() => go("admin")}>
              Admin console
              <LockKeyhole size={12} />
            </button>
          </div>
        </div>
        <div className="footer-bottom">
          <p>
            An ENGENE fan-made project. Not affiliated with or endorsed by MAMA,
            CJ ENM, HYBE, ENHYPEN or Twilight.
            <br />
            All race statistics reflect fan participation, not official MAMA
            voting results.
          </p>
          <span>FOR ENHYPEN. ALWAYS.</span>
        </div>
      </footer>
      <dialog
        ref={dialog}
        onCancel={() => setOnboard(false)}
        onClick={(e) => {
          if (e.target === dialog.current) setOnboard(false);
        }}
      >
        <div className="modal">
          <button
            className="close"
            onClick={() => setOnboard(false)}
            aria-label="Close sorting"
          >
            <X size={20} />
          </button>
          <div className="eyebrow">THE COVEN SORTING</div>
          {quizError && (
            <p className="quiz-error" role="alert">
              {quizError}
            </p>
          )}
          {step === "country" && (
            <>
              <Globe2 className="modal-symbol" size={38} strokeWidth={1} />
              <h2>
                Where are you
                <br />
                <em>voting from?</em>
              </h2>
              <p>
                Your country helps us gather across timezones.
                <br />
                You’ll join the race only after accepting your coven.
              </p>
              <label htmlFor="country-search">
                Search country or territory
              </label>
              <input
                id="country-search"
                placeholder="Type a country…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
              <div className="country-list" role="listbox" aria-label="Country">
                {countries
                  .filter((c) =>
                    (c.name + " " + c.alpha2 + " " + c.alpha3)
                      .toLowerCase()
                      .includes(search.toLowerCase()),
                  )
                  .map((c) => (
                    <button
                      key={c.id}
                      role="option"
                      aria-selected={country === c.id}
                      className={country === c.id ? "selected" : ""}
                      onClick={() => setCountry(c.id)}
                    >
                      <span>
                        {c.flag} {c.name}
                      </span>
                      {country === c.id && <Check size={17} />}
                    </button>
                  ))}
              </div>
              {selectedCountry && (
                <div className="country-meta">
                  <span>
                    {selectedCountry.region} · {selectedCountry.alpha2} /{" "}
                    {selectedCountry.alpha3} / {selectedCountry.numeric}
                  </span>
                  <span>
                    {selectedCountry.timezone} ·{" "}
                    {offset(selectedCountry.timezone, clock)}
                  </span>
                  <small>
                    Primary country timezone. No manual entry needed.
                  </small>
                </div>
              )}
              <button
                className="primary full"
                disabled={!country}
                onClick={() => setStep("intro")}
              >
                CONTINUE
                <ArrowRight size={16} />
              </button>
            </>
          )}
          {step === "intro" && (
            <>
              <Moon className="modal-symbol" size={45} strokeWidth={1} />
              <h2>
                {cfg.quiz.title === defaultConfig.quiz.title ? (
                  <>
                    What kind of
                    <br />
                    <em>ENGENE are you?</em>
                  </>
                ) : (
                  cfg.quiz.title
                )}
              </h2>
              <p>
                Every ENGENE supports ENHYPEN in their own way. Which coven
                reflects yours?
              </p>
              <div className="intro-quiz">
                8 QUESTIONS <span>✦</span> NO WRONG ANSWERS
              </div>
              <p>
                Answer based on how you naturally support ENHYPEN. This is an
                entertainment experience, not a personality assessment.
              </p>
              <button
                className="primary full"
                onClick={() => {
                  setQ(0);
                  setStep("questions");
                }}
              >
                BEGIN THE SORTING
                <ArrowRight size={16} />
              </button>
              <button className="back" onClick={() => setStep("country")}>
                <ChevronLeft size={14} /> Change country
              </button>
            </>
          )}
          {step === "questions" && (
            <>
              <div className="quiz-progress">
                <span>QUESTION {q + 1} / 8</span>
                <span>
                  {selectedCountry?.flag} {selectedCountry?.name}
                </span>
              </div>
              <div className="quiz-track">
                <div style={{ width: ((q + 1) / 8) * 100 + "%" }} />
              </div>
              <h2 className="question-title">{cfg.questions[q].title}</h2>
              <p>{cfg.questions[q].text}</p>
              <div className="answers">
                {cfg.questions[q].answers.map((a: string, i: number) => (
                  <button
                    className={answers[q] === i ? "selected" : ""}
                    key={a}
                    onClick={() =>
                      setAnswers((prev) => {
                        const a = [...prev];
                        a[q] = i;
                        return a;
                      })
                    }
                  >
                    <span>{String.fromCharCode(65 + i)}</span>
                    {a}
                    {answers[q] === i && <Check size={16} />}
                  </button>
                ))}
              </div>
              <div className="quiz-controls">
                <button
                  className="back"
                  onClick={() => (q ? setQ(q - 1) : setStep("intro"))}
                >
                  <ChevronLeft size={16} />
                  Back
                </button>
                <button
                  className="primary"
                  disabled={answers[q] === undefined || busy}
                  onClick={() => (q < 7 ? setQ(q + 1) : finish())}
                >
                  {busy
                    ? "THE NIGHT IS DECIDING…"
                    : q === 7
                      ? "REVEAL MY COVEN"
                      : "NEXT QUESTION"}
                  <ArrowRight size={16} />
                </button>
              </div>
            </>
          )}
          {step === "tie" && (
            <>
              <Moon className="modal-symbol" size={42} strokeWidth={1} />
              <h2>
                The night
                <br />
                <em>has not decided.</em>
              </h2>
              <p>
                ENHYPEN needs support right now.
                <br />
                Among these instincts, what comes most naturally?
              </p>
              <div className="answers">
                {tied.map((id) => (
                  <button disabled={busy} key={id} onClick={() => finish(id)}>
                    {
                      (
                        {
                          cullen: "Bring ENGENEs together.",
                          volturi: "Take immediate action.",
                          denali: "Stay consistent.",
                          egyptian: "Find another way forward.",
                        } as any
                      )[id]
                    }
                    <ArrowRight size={15} />
                  </button>
                ))}
              </div>
            </>
          )}
          {step === "result" && result && (
            <div className="reveal">
              <div className="eyebrow">THE NIGHT HAS CHOSEN YOU</div>
              <Crest clan={result.clan} />
              <h2>{clans.find((c: any) => c.id === result.clan)?.name}</h2>
              <p>{clans.find((c: any) => c.id === result.clan)?.description}</p>
              <div className="result-location">
                {selectedCountry?.flag} {selectedCountry?.name}
                <span>
                  {selectedCountry?.region} · {selectedCountry?.timezone}
                </span>
              </div>
              <div className="eyebrow">THIS IS YOUR COVEN.</div>
              <button className="primary full" disabled={busy} onClick={accept}>
                {busy ? "JOINING THE GATHERING…" : "ACCEPT MY CLAN"}
                <ArrowUpRight size={16} />
              </button>
              <p className="fine">
                Accepting creates your participant record and locks your clan
                for future race days.
              </p>
              <button
                className="back"
                disabled={busy}
                onClick={() => {
                  setAnswers([]);
                  setResult(null);
                  setQ(0);
                  setStep("intro");
                }}
              >
                <RefreshCw size={13} /> Retake quiz
              </button>
            </div>
          )}
        </div>
      </dialog>
    </>
  );
}
