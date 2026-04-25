import React from 'react';

function ImageGrid({ images }) {
    if (!images || images.length === 0) {
        return <div className="no-images-text">No images found. Try another search.</div>;
    }

    return (
        <div className="image-masonry-grid">
            {images.map((img, index) => (
                <div key={index} className="image-masonry-card">
                    <a href={img.sourceUrl} target="_blank" rel="noopener noreferrer">
                        {/* Use thumbnail for fast loading, fallback to raw URL */}
                        <img
                            src={img.thumbnailUrl || img.imageUrl}
                            alt={img.title}
                            className="masonry-img"
                            loading="lazy"
                        />
                        {/* Hover Overlay */}
                        <div className="masonry-overlay">
                            <p className="masonry-title" title={img.title}>{img.title}</p>
                            <span className="masonry-icon">↗</span>
                        </div>
                    </a>
                </div>
            ))}
        </div>
    );
}

export default ImageGrid;