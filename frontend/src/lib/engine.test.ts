import { describe, it, expect } from "vitest";
import { sortQuiz, gatheringDate, windows, leader, percentage } from "./engine";
import { defaultConfig } from "./config";
describe("gathering and sorting rules", () => {
  it("produces all four covens", () => {
    ["cullen", "volturi", "denali", "egyptian"].forEach((clan, i) =>
      expect(sortQuiz(Array(8).fill(i), defaultConfig.scoring).clan).toBe(clan),
    );
  });
  it("uses a deterministic tie-break restricted to tied clans", () => {
    const answers = [0, 0, 0, 0, 1, 1, 1, 1];
    expect(sortQuiz(answers, defaultConfig.scoring).tied).toEqual([
      "cullen",
      "volturi",
    ]);
    expect(sortQuiz(answers, defaultConfig.scoring, "volturi").clan).toBe(
      "volturi",
    );
    expect(sortQuiz(answers, defaultConfig.scoring, "denali").clan).toBeNull();
  });
  it("starts the new day at midnight in Seoul", () => {
    expect(gatheringDate(new Date("2026-10-11T14:59:59Z"))).toBe("2026-10-11");
    expect(gatheringDate(new Date("2026-10-11T15:00:00Z"))).toBe("2026-10-12");
  });
  it("excludes the exact end of a schedule", () => {
    expect(
      windows("2026-10-11", undefined, Date.parse("2026-10-11T02:00:00Z"))[0]
        .status,
    ).toBe("ENDED");
  });
  it("has no fabricated leader, handles ties and zero denominators", () => {
    expect(leader([{ clan: "cullen", percentage: 0 }])).toEqual([]);
    expect(percentage(0, 0)).toBe(0);
    expect(
      leader([
        { clan: "cullen", percentage: 50 },
        { clan: "denali", percentage: 50 },
      ]),
    ).toEqual(["cullen", "denali"]);
  });
});
