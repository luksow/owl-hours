"""Generate golden BAC curves from the authors' SUND implementation of alcohol_model_2.

Reads modules/model/jvm/src/test/resources/golden/scenarios.json and writes one CSV per scenario
next to it. Overlapping drinks are mixed as a volume-rate-weighted average, written independently
of the Scala implementation so the golden test also checks the mixing logic.
"""
import csv
import json
import pathlib

import numpy as np
import sund

HERE = pathlib.Path(__file__).resolve().parent
GOLDEN = HERE.parent / "modules/model/jvm/src/test/resources/golden"
BAC = "Blood alcohol concentration (mg/dL)"
BRAC = "Breath alcohol concentration (mg/L)"


EPS = 1e-4  # minutes; a pause this short changes intake by microlitres


def drink_series(drinks):
    """Piecewise-constant series: f has one value before the first breakpoint and one after each.

    Every drink is its own drinking episode, as in the authors' app: when a drink starts while another is still
    running, a pause of EPS minutes is inserted before it so SUND's new_drink event (rising edge) fires.
    """
    def active(x):
        return [d for d in drinks if d["startMin"] <= x < d["startMin"] + d["durationMin"]]

    gaps = {d["startMin"] - EPS for d in drinks if active(d["startMin"] - EPS)}
    points = sorted({d["startMin"] for d in drinks} | {d["startMin"] + d["durationMin"] for d in drinks} | gaps)
    conc, rate, kcal = [0.0], [0.0], [0.0]
    for i, a in enumerate(points):
        last = i == len(points) - 1
        current = [] if last or a in gaps else active(a)
        rates = [d["volumeMl"] / 1000.0 / d["durationMin"] for d in current]
        total = sum(rates)
        rate.append(total)
        conc.append(sum(r * d["abv"] for r, d in zip(rates, current)) / total if total > 0 else 0.0)
        kcal.append(sum(r * d["kcalPerLitre"] for r, d in zip(rates, current)) / total if total > 0 else 0.0)
    return points, conc, rate, kcal


def meal_series(meals):
    times = sorted(m["atMin"] for m in meals)
    assert all(b - a >= 2 for a, b in zip(times, times[1:])), "meals must be at least 2 min apart"
    t, f = [], [0.0]
    for m in sorted(meals, key=lambda m: m["atMin"]):
        t += [m["atMin"], m["atMin"] + 1]
        f += [m["kcal"], 0.0]
    return t, f


def run(model, scenario):
    act = sund.Activity(time_unit="m")
    points, conc, rate, kcal = drink_series(scenario["drinks"])
    act.add_output(name="EtOH_conc", type="piecewise_constant", t=points, f=conc)
    act.add_output(name="vol_drink_per_time", type="piecewise_constant", t=points, f=rate)
    act.add_output(name="kcal_liquid_per_vol", type="piecewise_constant", t=points, f=kcal)
    if scenario["meals"]:
        mt, mf = meal_series(scenario["meals"])
        act.add_output(name="kcal_solid", type="piecewise_constant", t=mt, f=mf)
    else:
        act.add_output(name="kcal_solid", type="constant", f=0.0)
    p = scenario["profile"]
    constants = {"sex": 1.0 if p["sex"] == "male" else 0.0, "weight": p["weightKg"],
                 "height": p["heightM"], "age": p["age"], "urinate": 0.0}
    for name, value in constants.items():
        act.add_output(name=name, type="constant", f=float(value))
    sim = sund.Simulation(models=model, activities=act, time_unit="m")
    times = np.arange(0.0, scenario["horizonMin"] + 1e-9, scenario["stepMin"])
    sim.simulate(time=times)
    names = list(sim.feature_names)
    values = np.array(sim.feature_values)
    return sim.time_vector, values[:, names.index(BAC)], values[:, names.index(BRAC)]


def main():
    sund.install_model(str(HERE / "alcohol_model_2.txt"))
    model = sund.load_model("alcohol_model_2")
    scenarios = json.loads((GOLDEN / "scenarios.json").read_text())
    for s in scenarios:
        t, bac, brac = run(model, s)
        with open(GOLDEN / f"{s['name']}.csv", "w", newline="") as fh:
            w = csv.writer(fh)
            w.writerow(["t_min", "bac_mgdl", "brac_mgl"])
            for row in zip(t, bac, brac):
                w.writerow([f"{x:.10g}" for x in row])
        print(f"{s['name']}: peak {max(bac):.3f} mg/dL")


if __name__ == "__main__":
    main()
