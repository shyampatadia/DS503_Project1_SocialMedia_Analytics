"""Utility functions for dataset generation - Vectorized for speed."""

import numpy as np
from numpy.random import Generator

# Quirky nickname components (no commas)
ADJECTIVES = np.array([
    "Sneaky", "Fluffy", "Grumpy", "Sleepy", "Dizzy", "Fuzzy", "Spicy", "Crispy",
    "Chunky", "Wonky", "Cranky", "Funky", "Salty", "Peppy", "Zesty", "Mighty",
    "Tiny", "Giant", "Swift", "Lazy", "Crazy", "Hazy", "Noisy", "Silent",
    "Cosmic", "Turbo", "Ultra", "Mega", "Epic", "Stealth", "Rogue", "Noble"
])

ANIMALS = np.array([
    "Penguin", "Llama", "Otter", "Panda", "Koala", "Sloth", "Badger", "Wombat",
    "Quokka", "Capybara", "Axolotl", "Narwhal", "Platypus", "Armadillo", "Lemur",
    "Raccoon", "Hedgehog", "Meerkat", "Chinchilla", "Ferret", "Walrus", "Moose",
    "Pelican", "Toucan", "Octopus", "Lobster", "Shrimp", "Squid", "Gecko", "Newt"
])

FOODS = np.array([
    "Pickle", "Waffle", "Taco", "Noodle", "Muffin", "Pretzel", "Nugget", "Biscuit",
    "Tofu", "Dumpling", "Burrito", "Pancake", "Croissant", "Bagel", "Donut", "Cookie",
    "Nacho", "Churro", "Ramen", "Sushi", "Pizza", "Kebab", "Falafel", "Samosa"
])

OBJECTS = np.array([
    "Sock", "Spoon", "Bucket", "Pixel", "Widget", "Gadget", "Gizmo", "Doodle",
    "Noodle", "Puddle", "Bobble", "Wobble", "Sprocket", "Cactus", "Tornado",
    "Volcano", "Comet", "Asteroid", "Nebula", "Glitch", "Blip", "Zap", "Bonk"
])

VERBS = np.array([
    "Dancing", "Snoring", "Yelling", "Running", "Flying", "Jumping", "Sleeping",
    "Spinning", "Bouncing", "Tumbling", "Zooming", "Vibing", "Lurking", "Prowling"
])

TITLES = np.array([
    "Lord", "Baron", "Captain", "Doctor", "Professor", "Admiral", "General",
    "Duke", "Count", "Sir", "Agent", "Detective", "Master", "Overlord", "King"
])

SUFFIXES = np.array(["Lover", "Fan", "King", "Queen", "Boss", "Lord"])

# Job components
JOB_PREFIXES_NORMAL = np.array([
    "Senior", "Junior", "Lead", "Chief", "Head", "Principal", "Staff",
    "Associate", "Assistant", "Executive", "Regional", "Global"
])

JOB_PREFIXES_QUIRKY = np.array([
    "Supreme", "Honorary", "Legendary", "Aspiring", "Reformed", "Undercover"
])

JOB_ROLES_NORMAL = np.array([
    "Engineer", "Developer", "Manager", "Analyst", "Designer", "Architect",
    "Consultant", "Specialist", "Coordinator", "Director", "Administrator",
    "Technician", "Scientist", "Researcher", "Strategist", "Officer"
])

JOB_ROLES_QUIRKY = np.array([
    "Wrangler", "Whisperer", "Wizard", "Ninja", "Guru", "Evangelist",
    "Herder", "Tamer", "Champion", "Enthusiast", "Sommelier", "Artisan"
])

JOB_DOMAINS_NORMAL = np.array([
    "Software", "Data", "Product", "Marketing", "Sales", "Finance",
    "Operations", "Security", "Quality", "Systems", "Network", "Cloud",
    "Mobile", "Frontend", "Backend", "Platform", "Infrastructure"
])

JOB_DOMAINS_QUIRKY = np.array([
    "Cat", "Coffee", "Meme", "Chaos", "Snack", "Nap", "Emoji", "Vibes",
    "Spreadsheet", "Meeting", "Deadline", "Bug", "Feature", "Keyboard"
])


def generate_nicknames_batch(rng: Generator, size: int, min_len: int, max_len: int) -> np.ndarray:
    """Generate batch of quirky nicknames - vectorized."""
    # Choose pattern for each nickname (0-9)
    patterns = rng.integers(0, 10, size=size)

    # Pre-generate all random indices we might need
    adj_idx = rng.integers(0, len(ADJECTIVES), size=size)
    animal_idx = rng.integers(0, len(ANIMALS), size=size)
    food_idx = rng.integers(0, len(FOODS), size=size)
    obj_idx = rng.integers(0, len(OBJECTS), size=size)
    verb_idx = rng.integers(0, len(VERBS), size=size)
    title_idx = rng.integers(0, len(TITLES), size=size)
    suffix_idx = rng.integers(0, len(SUFFIXES), size=size)
    numbers = rng.integers(1, 9999, size=size)

    results = np.empty(size, dtype=object)

    # Vectorized pattern application
    # Pattern 0: Adjective + Animal
    mask = patterns == 0
    results[mask] = np.char.add(ADJECTIVES[adj_idx[mask]], ANIMALS[animal_idx[mask]])

    # Pattern 1: Adjective + Food
    mask = patterns == 1
    results[mask] = np.char.add(ADJECTIVES[adj_idx[mask]], FOODS[food_idx[mask]])

    # Pattern 2: Title + Animal
    mask = patterns == 2
    results[mask] = np.char.add(TITLES[title_idx[mask]], ANIMALS[animal_idx[mask]])

    # Pattern 3: Verb + Animal
    mask = patterns == 3
    results[mask] = np.char.add(VERBS[verb_idx[mask]], ANIMALS[animal_idx[mask]])

    # Pattern 4: Animal + Number
    mask = patterns == 4
    results[mask] = np.char.add(ANIMALS[animal_idx[mask]], numbers[mask].astype(str))

    # Pattern 5: Food + Object
    mask = patterns == 5
    results[mask] = np.char.add(FOODS[food_idx[mask]], OBJECTS[obj_idx[mask]])

    # Pattern 6: The + Adjective + Object
    mask = patterns == 6
    results[mask] = np.char.add("The", np.char.add(ADJECTIVES[adj_idx[mask]], OBJECTS[obj_idx[mask]]))

    # Pattern 7: xX_Name_Xx gamer style
    mask = patterns == 7
    inner = np.char.add(ADJECTIVES[adj_idx[mask]], ANIMALS[animal_idx[mask]])
    results[mask] = np.char.add("xX", np.char.add(inner, "Xx"))

    # Pattern 8: Food + Suffix
    mask = patterns == 8
    results[mask] = np.char.add(FOODS[food_idx[mask]], SUFFIXES[suffix_idx[mask]])

    # Pattern 9: NotA + Animal
    mask = patterns == 9
    results[mask] = np.char.add("NotA", ANIMALS[animal_idx[mask]])

    # Ensure length constraints - add numbers if too short, truncate if too long
    for i in range(size):
        name = results[i]
        if len(name) < min_len:
            results[i] = name + str(rng.integers(100, 9999))
        if len(results[i]) > max_len:
            results[i] = results[i][:max_len]

    return results


def generate_job_titles_batch(rng: Generator, size: int, min_len: int, max_len: int) -> np.ndarray:
    """Generate batch of job titles - vectorized."""
    is_quirky = rng.random(size) < 0.3
    has_prefix = rng.random(size) > 0.4
    has_domain = rng.random(size) > 0.3

    results = np.empty(size, dtype=object)

    for i in range(size):
        parts = []

        if is_quirky[i]:
            if has_prefix[i]:
                parts.append(JOB_PREFIXES_QUIRKY[rng.integers(0, len(JOB_PREFIXES_QUIRKY))])
            parts.append(JOB_DOMAINS_QUIRKY[rng.integers(0, len(JOB_DOMAINS_QUIRKY))])
            parts.append(JOB_ROLES_QUIRKY[rng.integers(0, len(JOB_ROLES_QUIRKY))])
        else:
            if has_prefix[i]:
                parts.append(JOB_PREFIXES_NORMAL[rng.integers(0, len(JOB_PREFIXES_NORMAL))])
            if has_domain[i]:
                parts.append(JOB_DOMAINS_NORMAL[rng.integers(0, len(JOB_DOMAINS_NORMAL))])
            parts.append(JOB_ROLES_NORMAL[rng.integers(0, len(JOB_ROLES_NORMAL))])

        title = " ".join(parts)

        # Ensure length constraints
        if len(title) < min_len:
            # Add another domain word
            title = f"{JOB_DOMAINS_NORMAL[rng.integers(0, len(JOB_DOMAINS_NORMAL))]} {title}"
        if len(title) > max_len:
            title = title[:max_len]

        results[i] = title

    return results


def generate_descriptions_batch(rng: Generator, templates: list[str], indices: np.ndarray, size: int) -> np.ndarray:
    """Generate batch of descriptions from templates."""
    templates_arr = np.array(templates)
    return templates_arr[indices]


# single-item functions (for compatibility)
def generate_nickname(rng: Generator, min_len: int, max_len: int) -> str:
    return generate_nicknames_batch(rng, 1, min_len, max_len)[0]


def generate_job_title(rng: Generator, min_len: int, max_len: int) -> str:
    return generate_job_titles_batch(rng, 1, min_len, max_len)[0]


def generate_description(rng: Generator, templates: list[str], min_len: int = 20, max_len: int = 50) -> str:
    idx = rng.integers(0, len(templates))
    return templates[idx]
