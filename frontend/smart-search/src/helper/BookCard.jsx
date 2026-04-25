function BookCard({ book, onClick }) {
    const image = book.image || book.imageUrl || book.coverImageUrl || book.productUrl || "/placeholder.jpg";

    return(
        <div className="book-card" onClick={onClick} style={{cursor: 'pointer'}}>
            <img
                src={image}
                alt={book.title}
                className="book-cover"
                onError={(e) => {
                    e.target.src = "https://placehold.co/150x220?text=No+Cover"
                }} // Fallback image
            />
            <div className="book-info">
                <h4 className="book-title" title={book.title}>{book.title}</h4>
                {book.author && book.author !== "N/A" ? (
                    <p className="book-author">{book.author}</p>
                ) : (
                    <p className="book-author" style={{ fontStyle: 'italic', color: '#888' }}>
                        Author details unavailable
                    </p>
                )}
            </div>
        </div>
    )
}

export default BookCard