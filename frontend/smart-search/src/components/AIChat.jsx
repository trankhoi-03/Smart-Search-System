import {useEffect, useRef, useState} from "react";

function AIChat() {
    const [isOpen, setIsOpen] = useState(false);
    const [messages, setMessages] = useState([
        { type: 'ai', text: "Hi! I'm your Book Recommendation Assistant. What are you in the mood for today?" }
    ]);
    const [input, setInput] = useState('');
    const [isLoading, setIsLoading] = useState(false);

    const messagesEndRef = useRef(null);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    };

    useEffect(scrollToBottom, [messages]);

    const sendMessage = async () => {
        if (!input.trim() || isLoading) return;

        const userMsg = { type: 'user', text: input };
        setMessages(prev => [...prev, userMsg]);
        const currentInput = input;
        setInput('');
        setIsLoading(true);

        try {
            const storedData = localStorage.getItem("token");
            const res = await fetch('http://localhost:8080/books/chat/recommend', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${JSON.parse(storedData).token}`
                },
                body: JSON.stringify({ message: currentInput })
            });

            const data = await res.json();

            setMessages(prev => [...prev, {
                type: 'ai',
                text: data.reply,
                books: data.books || []
            }]);
        } catch (err) {
            setMessages(prev => [...prev, { type: 'ai', text: "Sorry, something went wrong. Please try again." }]);
        }
        setIsLoading(false);
    };

    return (
        <>
            {/* Floating Button */}
            <button
                className="chat-button"
                onClick={() => setIsOpen(!isOpen)}
            >
                💬
            </button>

            {/* Chat Window */}
            {isOpen && (
                <div className="chat-window">
                    {/* Header */}
                    <div className="chat-header-container">
                        <h3 className="chat-header">📖 Book Assistant</h3>
                        <button className="closed-chat-button" onClick={() => setIsOpen(false)}>✕</button>
                    </div>

                    {/* Messages */}
                    <div className="chat-message-container">
                        {messages.map((msg, i) => (
                            <div className="chat-message" key={i} style={{
                                alignSelf: msg.type === 'user' ? 'flex-end' : 'flex-start',
                                background: msg.type === 'user' ? '#3b82f6' : '#374151',
                                borderBottomRightRadius: msg.type === 'user' ? '4px' : '18px',
                                borderBottomLeftRadius: msg.type === 'user' ? '18px' : '4px'
                            }}>
                                {msg.text}
                                {msg.books && msg.books.length > 0 && (
                                    <div className="chat-display">
                                        {msg.books.map((book, idx) => (
                                            <div key={idx} style={{ background: '#1f2937', padding: '8px', borderRadius: '8px', display: 'flex', gap: '10px' }}>
                                                {book.imageUrl && <img src={book.imageUrl} alt="" style={{ width: '50px', height: '70px', objectFit: 'cover' }} />}
                                                <div>
                                                    <div style={{ fontWeight: 600 }}>{book.title}</div>
                                                    <div style={{ fontSize: '0.85rem', opacity: 0.9 }}>{book.author}</div>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>
                        ))}
                        {isLoading && <div style={{ alignSelf: 'flex-start', color: '#9ca3af' }}>Thinking...</div>}
                        <div ref={messagesEndRef} />
                    </div>

                    {/* Input */}
                    <div className="chat-input-container">
                        <input
                            className="chat-input"
                            value={input}
                            onChange={e => setInput(e.target.value)}
                            onKeyDown={e => e.key === 'Enter' && sendMessage()}
                            placeholder="E.g. Something like Dune but in space..."
                        />
                        <button className="chat-send-button" onClick={sendMessage} disabled={isLoading}>
                            Send
                        </button>
                    </div>
                </div>
            )}
        </>
    );
}

export default AIChat;