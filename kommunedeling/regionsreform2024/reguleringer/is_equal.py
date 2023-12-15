import json
from deepdiff import DeepDiff


with open('komplett24.json', 'r', encoding='utf8') as f:
    komplett24_postman = json.load(f)

with open('regulering-20231215-1053.json', 'r', encoding='utf8') as f:
    komplett24_generert = json.load(f)


def sort_dict(d):
    if isinstance(d, dict):
        return {k: sort_dict(v) for k, v in sorted(d.items())}
    elif isinstance(d, list):
        return sorted(sort_dict(x) if isinstance(x, dict) else x for x in d)
    return d

def find_unique(dict1, dict2):
    unique_transformations_dict1 = set(map(str, [sort_dict(trans) 
                                                 for endring in dict1['endringer'] 
                                                 for trans in endring['transformasjoner']]))
    unique_transformations_dict2 = set(map(str, [sort_dict(trans) 
                                                 for endring in dict2['endringer'] 
                                                 for trans in endring['transformasjoner']]))

    diffs = DeepDiff(unique_transformations_dict1, unique_transformations_dict2)
    
    if diffs:
        print("Files are not identical")
        print("Differences:", diffs)
    else:
        print("Files are identical")


find_unique(komplett24_postman, komplett24_generert)
