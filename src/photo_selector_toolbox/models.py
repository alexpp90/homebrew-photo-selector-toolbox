from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Optional, Union


@dataclass
class ExifData:
    """Typed EXIF metadata extracted from an image."""

    shutter_speed: Optional[float] = None
    aperture: Optional[float] = None
    focal_length: Optional[float] = None
    focal_length_35mm: Optional[float] = None
    is_fallback: bool = False
    iso: Optional[float] = None
    lens: str = "Unknown"


@dataclass
class ScanResult:
    """Represents the analysis result for a single image."""

    path: Path
    scores: Dict[str, Union[float, str]] = field(default_factory=dict)
    exif: Optional[ExifData] = None

    def __init__(
        self,
        path: Path,
        score: Union[float, str] = "N/A",
        noise_score: Union[float, str] = "N/A",
        scores: Optional[Dict[str, Union[float, str]]] = None,
        exif: Optional[ExifData] = None,
    ):
        self.path = path
        self.scores = scores if scores is not None else {}
        if score != "N/A":
            self.scores["sharpness"] = score
        if noise_score != "N/A":
            self.scores["noise"] = noise_score
        self.exif = exif

    @property
    def score(self) -> Union[float, str]:
        """Backward-compatible accessor for sharpness score."""
        return self.scores.get("sharpness", "N/A")

    @score.setter
    def score(self, val: Union[float, str]):
        self.scores["sharpness"] = val

    @property
    def noise_score(self) -> Union[float, str]:
        """Backward-compatible accessor for noise score."""
        return self.scores.get("noise", "N/A")

    @noise_score.setter
    def noise_score(self, val: Union[float, str]):
        self.scores["noise"] = val

    @property
    def highlight_clipping(self) -> Union[float, str]:
        """Accessor for highlight clipping score."""
        return self.scores.get("highlight_clipping", "N/A")

    @highlight_clipping.setter
    def highlight_clipping(self, val: Union[float, str]):
        self.scores["highlight_clipping"] = val

    @property
    def shadow_clipping(self) -> Union[float, str]:
        """Accessor for shadow clipping score."""
        return self.scores.get("shadow_clipping", "N/A")

    @shadow_clipping.setter
    def shadow_clipping(self, val: Union[float, str]):
        self.scores["shadow_clipping"] = val

    @property
    def aesthetic_score(self) -> Union[float, str]:
        """Accessor for AI aesthetic evaluation score."""
        return self.scores.get("aesthetic", "N/A")

    @aesthetic_score.setter
    def aesthetic_score(self, val: Union[float, str]):
        self.scores["aesthetic"] = val
