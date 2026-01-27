import BookCard from "./BookCard.jsx";

function BookList({ title, books, onClick }) {
    if (!books || books.length === 0) return null

    return(
        <div className="source-section">
            <h3 className="source-title">{title}</h3>
            <div className="book-grid">
                {books.map((book, index) => (
                    <BookCard key={index} book={book} onClick={() => onClick(book)}/>
                ))}
            </div>

        </div>
    )
}

export default BookList