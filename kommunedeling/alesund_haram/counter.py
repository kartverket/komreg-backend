import json
from collections import defaultdict

def process_transformations(transformations):
    counts = defaultdict(lambda: defaultdict(int))
    num_changes = defaultdict(int)
    unchanged_kretsnummer = 0

    for transformation in transformations:
        type_ = transformation['type']
        kommunelopenummers = transformation['kommuneløpenummer']['til']

        if not isinstance(kommunelopenummers, list):
            kommunelopenummers = [kommunelopenummers]

        for key in transformation.keys():
            if key not in ['type', 'kommuneløpenummer']:
                if type_ == 'krets' and key == 'kretsnummer':
                    if transformation[key]['fra'] == transformation[key]['til']:
                        unchanged_kretsnummer += 1
                    else:
                        num_changes[key] += 1

        for kommunelopenummer in kommunelopenummers:
            counts[type_][kommunelopenummer] += 1

    return counts, num_changes, unchanged_kretsnummer

if __name__ == '__main__':
    with open("output.json", "r") as f:
        transformations = json.load(f)["transformasjoner"]

    counts, num_changes, unchanged_kretsnummer = process_transformations(transformations)

    for type_, data in counts.items():
        print(f"{type_}:")
        for k, v in sorted(data.items()):
            print(f"{k}: {v}")

    print(f"changed kretsnummer: {num_changes.get('kretsnummer', 0)}")
    print(f"unchanged kretsnummer: {unchanged_kretsnummer}")
