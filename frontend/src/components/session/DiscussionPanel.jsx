import React, { useState, useEffect, useRef } from 'react';
import { api } from '../../api/client';
import { Send, AlertCircle, MessageSquare } from 'lucide-react';

export const DiscussionPanel = ({ sessionId, stompClient, isConnected, user }) => {
  const [messages, setMessages] = useState([]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(true);
  const [sendError, setSendError] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const messagesEndRef = useRef(null);

  // Fetch initial message history
  useEffect(() => {
    let isMounted = true;
    const fetchHistory = async () => {
      try {
        const res = await api.get(`/sessions/${sessionId}/discussion?page=0&size=50`);
        if (isMounted) {
          setMessages(res.data.content || []);
          setLoading(false);
        }
      } catch (err) {
        if (isMounted) {
          console.error('Failed to load discussion history:', err);
          setLoading(false);
        }
      }
    };

    fetchHistory();
    return () => {
      isMounted = false;
    };
  }, [sessionId]);

  // Subscribe to STOMP topic
  useEffect(() => {
    if (!stompClient || !isConnected) return;

    const subscription = stompClient.subscribe(
      `/topic/sessions/${sessionId}/discussion`,
      (frame) => {
        try {
          const newMsg = JSON.parse(frame.body);
          setMessages((prev) => {
            // Avoid duplicate message if already added
            if (prev.some((m) => m.id === newMsg.id)) {
              return prev;
            }
            return [...prev, newMsg];
          });
        } catch (e) {
          console.error('Failed to parse incoming discussion message:', e);
        }
      }
    );

    return () => {
      try {
        subscription.unsubscribe();
      } catch (e) {
        console.warn('Subscription unsubscribe error during cleanup:', e);
      }
    };
  }, [stompClient, isConnected, sessionId]);

  // Auto-scroll on new message
  useEffect(() => {
    if (typeof messagesEndRef.current?.scrollIntoView === 'function') {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages]);

  const handleSendMessage = async (e) => {
    e.preventDefault();
    const content = inputValue.trim();
    if (!content || content.length > 1000 || isSubmitting) return;

    setSendError(null);
    setIsSubmitting(true);

    try {
      if (stompClient && isConnected) {
        // Deliver over STOMP
        stompClient.publish({
          destination: `/app/sessions/${sessionId}/discussion`,
          body: JSON.stringify({ content }),
        });
        setInputValue('');
      } else {
        // Send via REST endpoint when STOMP is not connected
        const res = await api.post(`/sessions/${sessionId}/discussion`, { content });
        setMessages((prev) => [...prev, res.data]);
        setInputValue('');
      }
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Failed to send message';
      setSendError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const formatTime = (isoString) => {
    if (!isoString) return '';
    const date = new Date(isoString);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  return (
    <div className="flex flex-col h-full bg-slate-900 border-l border-slate-800">
      {/* Messages Scroll Area */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {loading ? (
          <div className="text-center py-8 text-xs text-slate-500">
            Loading chat messages...
          </div>
        ) : messages.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-center p-6 text-slate-500">
            <MessageSquare className="w-8 h-8 text-slate-600 mb-2 stroke-1" />
            <p className="text-xs font-medium text-slate-400">No messages yet</p>
            <p className="text-[11px] text-slate-500 mt-0.5">Start the class conversation</p>
          </div>
        ) : (
          messages.map((msg) => {
            const isMe = msg.senderId === user?.id;
            const isTeacher = msg.senderRole === 'TEACHER';
            const isAdmin = msg.senderRole === 'ADMIN';

            return (
              <div
                key={msg.id}
                className={`flex flex-col ${isMe ? 'items-end' : 'items-start'}`}
              >
                <div className="flex items-center gap-1.5 mb-1 px-1">
                  <span className="text-[11px] font-semibold text-slate-300">
                    {msg.senderName}
                  </span>
                  {isTeacher && (
                    <span className="text-[9px] px-1.5 py-0.2 rounded font-medium bg-indigo-500/20 text-indigo-400 border border-indigo-500/30">
                      Teacher
                    </span>
                  )}
                  {isAdmin && (
                    <span className="text-[9px] px-1.5 py-0.2 rounded font-medium bg-purple-500/20 text-purple-400 border border-purple-500/30">
                      Admin
                    </span>
                  )}
                  <span className="text-[10px] text-slate-500">
                    {formatTime(msg.createdAt)}
                  </span>
                </div>
                <div
                  className={`max-w-[85%] rounded-2xl px-3.5 py-2 text-xs leading-relaxed break-words ${
                    isMe
                      ? 'bg-indigo-600 text-white rounded-br-none shadow-sm'
                      : 'bg-slate-800 text-slate-200 rounded-bl-none border border-slate-700/60'
                  }`}
                >
                  {msg.content}
                </div>
              </div>
            );
          })
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Error alert banner */}
      {sendError && (
        <div className="px-4 py-2 bg-rose-500/10 border-t border-rose-500/20 flex items-center gap-2 text-rose-400 text-xs">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span className="truncate flex-1">{sendError}</span>
          <button
            onClick={() => setSendError(null)}
            className="text-[10px] text-slate-400 hover:text-white"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Input Form */}
      <form onSubmit={handleSendMessage} className="p-3 bg-slate-950 border-t border-slate-800">
        <div className="relative">
          <input
            type="text"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            placeholder="Type a message... (max 1000 characters)"
            maxLength={1000}
            className="w-full bg-slate-900 border border-slate-800 text-slate-100 placeholder:text-slate-500 rounded-xl px-3.5 py-2.5 text-xs pr-10 focus:outline-none focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500 transition-colors"
          />
          <button
            type="submit"
            disabled={!inputValue.trim() || isSubmitting}
            className="absolute right-1.5 top-1/2 -translate-y-1/2 p-1.5 rounded-lg text-slate-400 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
        <div className="mt-1 flex justify-between items-center px-1 text-[10px] text-slate-500">
          <span>{isConnected ? 'Live delivery over WebSocket' : 'Offline / REST mode'}</span>
          <span>{inputValue.length}/1000</span>
        </div>
      </form>
    </div>
  );
};
