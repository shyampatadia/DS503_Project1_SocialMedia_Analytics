import argparse
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from mpl_toolkits.mplot3d import Axes3D  # noqa: F401
from sklearn.decomposition import PCA
from sklearn.manifold import TSNE
from sklearn.preprocessing import StandardScaler


def read_clustered_points(input_path: Path) -> pd.DataFrame:
    files = []
    if input_path.is_file():
        files = [input_path]
    else:
        files = sorted([p for p in input_path.glob("part-*") if p.is_file()])

    if not files:
        raise FileNotFoundError(f"No part files found in: {input_path}")

    frames = []
    for f in files:
        df = pd.read_csv(
            f,
            header=None,
            usecols=[0, 1, 2, 3, 4],
            names=["w", "x", "y", "z", "cluster"],
        )
        frames.append(df)

    out = pd.concat(frames, ignore_index=True)
    out["cluster"] = out["cluster"].astype(int)
    return out


def plot_embedding(emb: np.ndarray, clusters: np.ndarray, out_file: Path, title: str) -> None:
    plt.figure(figsize=(10, 7))
    unique_clusters = np.unique(clusters)

    for cid in unique_clusters:
        idx = clusters == cid
        plt.scatter(emb[idx, 0], emb[idx, 1], s=8, alpha=0.7, label=f"cluster_{cid}")

    plt.title(title)
    plt.xlabel("dim_1")
    plt.ylabel("dim_2")
    plt.legend(markerscale=2, fontsize=8)
    plt.tight_layout()
    plt.savefig(out_file, dpi=180)
    plt.close()


def plot_embedding_3d(emb: np.ndarray, clusters: np.ndarray, out_file: Path, title: str) -> None:
    fig = plt.figure(figsize=(10, 8))
    ax = fig.add_subplot(111, projection="3d")
    unique_clusters = np.unique(clusters)

    for cid in unique_clusters:
        idx = clusters == cid
        ax.scatter(
            emb[idx, 0],
            emb[idx, 1],
            emb[idx, 2],
            s=8,
            alpha=0.7,
            label=f"cluster_{cid}",
        )

    ax.set_title(title)
    ax.set_xlabel("dim_1")
    ax.set_ylabel("dim_2")
    ax.set_zlabel("dim_3")
    ax.legend(markerscale=2, fontsize=8)
    plt.tight_layout()
    plt.savefig(out_file, dpi=180)
    plt.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="PCA and t-SNE plots for clustered points")
    parser.add_argument("--input", required=True, help="Input file or folder (part-* files)")
    parser.add_argument("--output", required=True, help="Output folder for plots")
    parser.add_argument("--sample", type=int, default=10000, help="Max points to plot")
    parser.add_argument("--tsne-perplexity", type=float, default=30.0, help="t-SNE perplexity")
    parser.add_argument(
        "--standardize",
        action="store_true",
        help="Standardize w,x,y,z before PCA/t-SNE",
    )
    args = parser.parse_args()

    input_path = Path(args.input)
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    df = read_clustered_points(input_path)
    if len(df) > args.sample:
        df = df.sample(n=args.sample, random_state=42).reset_index(drop=True)

    x = df[["w", "x", "y", "z"]].to_numpy(dtype=float)
    y = df["cluster"].to_numpy(dtype=int)

    if args.standardize:
        scaler = StandardScaler()
        x = scaler.fit_transform(x)

    pca = PCA(n_components=2, random_state=42)
    x_pca = pca.fit_transform(x)
    plot_embedding(x_pca, y, output_dir / "pca_2d.png", "PCA 2D Projection")

    pca3 = PCA(n_components=3, random_state=42)
    x_pca3 = pca3.fit_transform(x)
    plot_embedding_3d(x_pca3, y, output_dir / "pca_3d.png", "PCA 3D Projection")

    tsne = TSNE(
        n_components=2,
        perplexity=args.tsne_perplexity,
        init="pca",
        learning_rate="auto",
        random_state=42,
    )
    x_tsne = tsne.fit_transform(x)
    plot_embedding(x_tsne, y, output_dir / "tsne_2d.png", "t-SNE 2D Projection")

    tsne3 = TSNE(
        n_components=3,
        perplexity=args.tsne_perplexity,
        init="pca",
        learning_rate="auto",
        random_state=42,
    )
    x_tsne3 = tsne3.fit_transform(x)
    plot_embedding_3d(x_tsne3, y, output_dir / "tsne_3d.png", "t-SNE 3D Projection")

    counts = df.groupby("cluster").size().reset_index(name="count")
    counts.to_csv(output_dir / "cluster_counts_sampled.csv", index=False)

    with open(output_dir / "summary.txt", "w", encoding="utf-8") as f:
        f.write(f"input={input_path}\n")
        f.write(f"sampled_points={len(df)}\n")
        f.write(f"standardized={args.standardize}\n")
        f.write(f"pca_variance_ratio={pca.explained_variance_ratio_.tolist()}\n")
        f.write(f"pca3_variance_ratio={pca3.explained_variance_ratio_.tolist()}\n")
        f.write(f"tsne_perplexity={args.tsne_perplexity}\n")

    print(f"Done. Outputs written to: {output_dir}")


if __name__ == "__main__":
    main()
