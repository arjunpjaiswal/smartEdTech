import React, { useState, useEffect } from 'react';
import { api } from '../../api/client';
import { Button } from '../common/Button';
import { Modal } from '../common/Modal';
import {
  BarChart2,
  Plus,
  CheckCircle2,
  XCircle,
  Eye,
  EyeOff,
  Radio,
  Lock,
  Unlock,
  AlertCircle,
  Trash2,
} from 'lucide-react';

export const PollPanel = ({ sessionId, stompClient, isConnected, user, isTeacherOrAdmin }) => {
  const [polls, setPolls] = useState([]);
  const [loading, setLoading] = useState(true);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [actionError, setActionError] = useState(null);

  // Form state for creating poll
  const [question, setQuestion] = useState('');
  const [options, setOptions] = useState(['', '']);
  const [resultsVisible, setResultsVisible] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Voting state (pollId -> selectedOptionId)
  const [selectedVotes, setSelectedVotes] = useState({});
  const [votingPollId, setVotingPollId] = useState(null);

  // Fetch initial polls
  useEffect(() => {
    let isMounted = true;
    const fetchPolls = async () => {
      try {
        const res = await api.get(`/sessions/${sessionId}/polls`);
        if (isMounted) {
          setPolls(res.data || []);
          setLoading(false);
        }
      } catch (err) {
        if (isMounted) {
          console.error('Failed to load session polls:', err);
          setLoading(false);
        }
      }
    };

    fetchPolls();
    return () => {
      isMounted = false;
    };
  }, [sessionId]);

  // Subscribe to STOMP poll topics
  useEffect(() => {
    if (!stompClient || !isConnected) return;

    const onPollReceived = (frame) => {
      try {
        const updatedPoll = JSON.parse(frame.body);
        setPolls((prev) => {
          const idx = prev.findIndex((p) => p.id === updatedPoll.id);
          if (idx >= 0) {
            const next = [...prev];
            next[idx] = updatedPoll;
            return next;
          }
          return [updatedPoll, ...prev];
        });
      } catch (e) {
        console.error('Failed to parse incoming poll update:', e);
      }
    };

    // Public / student subscription
    const sub = stompClient.subscribe(`/topic/sessions/${sessionId}/polls`, onPollReceived);

    // Teacher subscription (receives full vote counts even when hidden from students)
    let teacherSub = null;
    if (isTeacherOrAdmin) {
      teacherSub = stompClient.subscribe(
        `/topic/sessions/${sessionId}/polls/teacher`,
        onPollReceived
      );
    }

    return () => {
      try {
        sub.unsubscribe();
        if (teacherSub) teacherSub.unsubscribe();
      } catch (e) {
        console.warn('Poll subscription unsubscribe error during cleanup:', e);
      }
    };
  }, [stompClient, isConnected, sessionId, isTeacherOrAdmin]);

  const handleAddOption = () => {
    if (options.length < 10) {
      setOptions([...options, '']);
    }
  };

  const handleRemoveOption = (index) => {
    if (options.length > 2) {
      setOptions(options.filter((_, i) => i !== index));
    }
  };

  const handleOptionChange = (index, value) => {
    const next = [...options];
    next[index] = value;
    setOptions(next);
  };

  const handleCreatePoll = async (e) => {
    e.preventDefault();
    setActionError(null);

    const validOptions = options.map((o) => o.trim()).filter((o) => o.length > 0);
    if (!question.trim()) {
      setActionError('Question cannot be empty');
      return;
    }
    if (validOptions.length < 2) {
      setActionError('At least 2 non-empty options are required');
      return;
    }

    setSubmitting(true);
    try {
      const res = await api.post(`/sessions/${sessionId}/polls`, {
        question: question.trim(),
        options: validOptions,
        resultsVisibleToStudents: resultsVisible,
      });

      // Update state if not already delivered via STOMP
      setPolls((prev) => {
        if (prev.some((p) => p.id === res.data.id)) return prev;
        return [res.data, ...prev];
      });

      setCreateModalOpen(false);
      setQuestion('');
      setOptions(['', '']);
      setResultsVisible(false);
    } catch (err) {
      setActionError(err.response?.data?.message || 'Failed to create poll');
    } finally {
      setSubmitting(false);
    }
  };

  const handleOpenPoll = async (pollId) => {
    setActionError(null);
    try {
      const res = await api.patch(`/sessions/${sessionId}/polls/${pollId}/open`);
      setPolls((prev) => prev.map((p) => (p.id === pollId ? res.data : p)));
    } catch (err) {
      setActionError(err.response?.data?.message || 'Failed to open poll');
    }
  };

  const handleClosePoll = async (pollId) => {
    setActionError(null);
    try {
      const res = await api.patch(`/sessions/${sessionId}/polls/${pollId}/close`);
      setPolls((prev) => prev.map((p) => (p.id === pollId ? res.data : p)));
    } catch (err) {
      setActionError(err.response?.data?.message || 'Failed to close poll');
    }
  };

  const handleToggleVisibility = async (pollId, currentVisibility) => {
    setActionError(null);
    try {
      const res = await api.patch(`/sessions/${sessionId}/polls/${pollId}/visibility`, {
        visible: !currentVisibility,
      });
      setPolls((prev) => prev.map((p) => (p.id === pollId ? res.data : p)));
    } catch (err) {
      setActionError(err.response?.data?.message || 'Failed to update visibility');
    }
  };

  const handleVote = async (pollId) => {
    const optionId = selectedVotes[pollId];
    if (!optionId) return;

    setActionError(null);
    setVotingPollId(pollId);
    try {
      const res = await api.post(`/sessions/${sessionId}/polls/${pollId}/vote`, {
        optionId,
      });
      setPolls((prev) => prev.map((p) => (p.id === pollId ? res.data : p)));
    } catch (err) {
      setActionError(err.response?.data?.message || 'Failed to cast vote');
    } finally {
      setVotingPollId(null);
    }
  };

  return (
    <div className="flex flex-col h-full bg-slate-900 border-l border-slate-800">
      {/* Top Header / Actions */}
      <div className="p-3 bg-slate-950 border-b border-slate-800 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <BarChart2 className="w-4 h-4 text-indigo-400" />
          <span className="text-xs font-bold text-white">Live Polls</span>
          <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-slate-800 text-slate-400">
            {polls.length}
          </span>
        </div>

        {isTeacherOrAdmin && (
          <Button
            size="sm"
            variant="primary"
            icon={Plus}
            onClick={() => setCreateModalOpen(true)}
          >
            Create Poll
          </Button>
        )}
      </div>

      {actionError && (
        <div className="p-3 bg-rose-500/10 border-b border-rose-500/20 flex items-center gap-2 text-rose-400 text-xs">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span className="flex-1">{actionError}</span>
          <button
            onClick={() => setActionError(null)}
            className="text-[10px] text-slate-400 hover:text-white"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Polls List */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {loading ? (
          <div className="text-center py-8 text-xs text-slate-500">
            Loading polls...
          </div>
        ) : polls.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-center p-6 text-slate-500">
            <BarChart2 className="w-8 h-8 text-slate-600 mb-2 stroke-1" />
            <p className="text-xs font-medium text-slate-400">No polls yet</p>
            <p className="text-[11px] text-slate-500 mt-0.5">
              {isTeacherOrAdmin
                ? 'Create a quick poll to engage students during class'
                : 'Waiting for the teacher to launch a poll'}
            </p>
          </div>
        ) : (
          polls.map((poll) => {
            const isDraft = poll.status === 'DRAFT';
            const isOpen = poll.status === 'OPEN';
            const isClosed = poll.status === 'CLOSED';
            const hasVoted = poll.userVotedOptionId != null;
            const canVote = isOpen && !hasVoted;
            const showResults = poll.totalVotes != null;

            return (
              <div
                key={poll.id}
                className="bg-slate-950/70 border border-slate-800 rounded-xl p-3.5 space-y-3"
              >
                {/* Poll Header */}
                <div className="flex items-start justify-between gap-2">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <span
                        className={`text-[9px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wider ${
                          isOpen
                            ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                            : isClosed
                            ? 'bg-slate-800 text-slate-400 border border-slate-700'
                            : 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                        }`}
                      >
                        {poll.status}
                      </span>
                      {showResults && (
                        <span className="text-[10px] text-slate-400">
                          {poll.totalVotes} {poll.totalVotes === 1 ? 'vote' : 'votes'}
                        </span>
                      )}
                    </div>
                    <h3 className="text-xs font-semibold text-slate-100 leading-snug">
                      {poll.question}
                    </h3>
                  </div>
                </div>

                {/* Options / Voting Area */}
                <div className="space-y-2 pt-1">
                  {(poll.options || []).map((opt) => {
                    const isSelected = selectedVotes[poll.id] === opt.id;
                    const isMyVote = poll.userVotedOptionId === opt.id;
                    const pct = opt.percentage != null ? Math.round(opt.percentage) : null;

                    return (
                      <div
                        key={opt.id}
                        onClick={() => canVote && setSelectedVotes({ ...selectedVotes, [poll.id]: opt.id })}
                        className={`relative overflow-hidden rounded-lg p-2.5 text-xs transition-all ${
                          canVote
                            ? 'cursor-pointer hover:border-indigo-500/50 ' +
                              (isSelected
                                ? 'border border-indigo-500 bg-indigo-500/10'
                                : 'border border-slate-800 bg-slate-900/60')
                            : 'border border-slate-800 bg-slate-900/40'
                        }`}
                      >
                        {/* Percentage fill bar */}
                        {showResults && pct != null && (
                          <div
                            className="absolute inset-0 bg-indigo-600/15 pointer-events-none transition-all duration-500"
                            style={{ width: `${pct}%` }}
                          />
                        )}

                        <div className="relative flex items-center justify-between gap-2">
                          <div className="flex items-center gap-2">
                            {canVote ? (
                              <input
                                type="radio"
                                name={`poll-${poll.id}`}
                                checked={isSelected}
                                onChange={() => setSelectedVotes({ ...selectedVotes, [poll.id]: opt.id })}
                                className="text-indigo-600 focus:ring-indigo-500 bg-slate-950 border-slate-700"
                              />
                            ) : isMyVote ? (
                              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 flex-shrink-0" />
                            ) : null}
                            <span
                              className={`font-medium ${
                                isMyVote ? 'text-emerald-300 font-bold' : 'text-slate-200'
                              }`}
                            >
                              {opt.optionText}
                            </span>
                          </div>

                          {showResults && opt.voteCount != null && (
                            <div className="text-[11px] font-mono text-slate-400 flex items-center gap-1.5 flex-shrink-0">
                              <span>{pct}%</span>
                              <span className="text-slate-600">({opt.voteCount})</span>
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>

                {/* Student Vote Action */}
                {canVote && (
                  <div className="pt-1 flex justify-end">
                    <Button
                      size="sm"
                      variant="primary"
                      disabled={!selectedVotes[poll.id] || votingPollId === poll.id}
                      onClick={() => handleVote(poll.id)}
                    >
                      {votingPollId === poll.id ? 'Submitting...' : 'Submit Vote'}
                    </Button>
                  </div>
                )}

                {/* Has Voted Note */}
                {hasVoted && isOpen && !showResults && (
                  <p className="text-[11px] text-slate-400 italic text-center pt-1">
                    ✓ Your vote has been recorded. Results will be revealed by the teacher.
                  </p>
                )}

                {/* Teacher Management Controls */}
                {isTeacherOrAdmin && (
                  <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-xs">
                    <div className="flex items-center gap-1">
                      {isOpen && (
                        <button
                          onClick={() => handleToggleVisibility(poll.id, poll.resultsVisibleToStudents)}
                          className="px-2 py-1 rounded text-[11px] font-medium text-slate-400 hover:text-white flex items-center gap-1 transition-colors"
                          title={poll.resultsVisibleToStudents ? 'Hide results from students' : 'Show results to students'}
                        >
                          {poll.resultsVisibleToStudents ? (
                            <>
                              <Eye className="w-3 h-3 text-indigo-400" /> Results Public
                            </>
                          ) : (
                            <>
                              <EyeOff className="w-3 h-3 text-slate-500" /> Results Hidden
                            </>
                          )}
                        </button>
                      )}
                    </div>

                    <div className="flex items-center gap-1.5">
                      {isDraft && (
                        <Button
                          size="sm"
                          variant="primary"
                          icon={Unlock}
                          onClick={() => handleOpenPoll(poll.id)}
                        >
                          Open Poll
                        </Button>
                      )}
                      {isOpen && (
                        <Button
                          size="sm"
                          variant="secondary"
                          icon={Lock}
                          onClick={() => handleClosePoll(poll.id)}
                        >
                          Close Poll
                        </Button>
                      )}
                    </div>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>

      {/* Create Poll Modal */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        title="Create Live Poll"
      >
        <form onSubmit={handleCreatePoll} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">
              Poll Question
            </label>
            <input
              type="text"
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              placeholder="e.g. Which algorithm has O(log n) search time?"
              maxLength={1000}
              className="w-full bg-slate-900 border border-slate-700 text-slate-100 placeholder:text-slate-500 rounded-xl px-3.5 py-2.5 text-xs focus:outline-none focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500"
            />
          </div>

          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="text-xs font-semibold text-slate-300">
                Answer Options (2 - 10)
              </label>
              {options.length < 10 && (
                <button
                  type="button"
                  onClick={handleAddOption}
                  className="text-[11px] font-semibold text-indigo-400 hover:text-indigo-300 flex items-center gap-1"
                >
                  <Plus className="w-3 h-3" /> Add Option
                </button>
              )}
            </div>

            <div className="space-y-2">
              {options.map((opt, idx) => (
                <div key={idx} className="flex items-center gap-2">
                  <span className="text-[11px] font-mono text-slate-500 w-4 text-center">
                    {idx + 1}.
                  </span>
                  <input
                    type="text"
                    value={opt}
                    onChange={(e) => handleOptionChange(idx, e.target.value)}
                    placeholder={`Option ${idx + 1}`}
                    maxLength={500}
                    className="flex-1 bg-slate-900 border border-slate-700 text-slate-100 placeholder:text-slate-500 rounded-xl px-3 py-2 text-xs focus:outline-none focus:ring-1 focus:ring-indigo-500"
                  />
                  {options.length > 2 && (
                    <button
                      type="button"
                      onClick={() => handleRemoveOption(idx)}
                      className="p-1.5 text-slate-500 hover:text-rose-400"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>

          <div className="pt-2 border-t border-slate-800">
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={resultsVisible}
                onChange={(e) => setResultsVisible(e.target.checked)}
                className="rounded text-indigo-600 focus:ring-indigo-500 bg-slate-900 border-slate-700"
              />
              <span className="text-xs text-slate-300">
                Reveal live voting results to students immediately
              </span>
            </label>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button
              variant="secondary"
              type="button"
              onClick={() => setCreateModalOpen(false)}
            >
              Cancel
            </Button>
            <Button variant="primary" type="submit" disabled={submitting}>
              {submitting ? 'Creating...' : 'Create Poll'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
