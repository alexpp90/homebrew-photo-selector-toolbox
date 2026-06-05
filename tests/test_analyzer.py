def test_analyzer_no_data(caplog):
    import logging
    from photo_selector_toolbox.analyzer import analyze_data

    caplog.set_level(logging.INFO)
    analyze_data([])
    assert "No data to analyze" in caplog.text


def test_analyzer_basic_stats(caplog):
    import logging
    from photo_selector_toolbox.analyzer import analyze_data
    from photo_selector_toolbox.models import ExifData

    data = [
        ExifData(
            shutter_speed=0.01,
            aperture=2.8,
            focal_length=50.0,
            iso=100.0,
            lens="Lens A",
        ),
        ExifData(
            shutter_speed=0.02,
            aperture=4.0,
            focal_length=50.0,
            iso=200.0,
            lens="Lens A",
        ),
        ExifData(
            shutter_speed=0.01,
            aperture=2.8,
            focal_length=85.0,
            iso=100.0,
            lens="Lens B",
        ),
    ]
    caplog.set_level(logging.INFO)
    analyze_data(data)
    assert "Total images with EXIF data analyzed: 3" in caplog.text
    assert "Top 5 Lenses:" in caplog.text
    assert "Lens A: 2" in caplog.text
    assert "Lens B: 1" in caplog.text
    assert "Top 5 ISOs:" in caplog.text
    assert "100: 2" in caplog.text
    assert "200: 1" in caplog.text
    assert "f/2.8 @ 50mm: 1" in caplog.text
