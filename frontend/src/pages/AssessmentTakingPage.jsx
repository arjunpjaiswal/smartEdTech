import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api } from '../api/client';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Modal } from '../components/common/Modal';
import { ErrorState } from '../components/common/ErrorState';
import { Skeleton } from '../components/common/Skeleton';
import { useToast } from '../components/common/Toast';
import {
  Clock,
  AlertTriangle,
  CheckCircle,
  ArrowLeft,
  ArrowRight,
  Send,
  Loader2,
  HelpCircle,
} from 'lucide-react';

export const AssessmentTakingPage = () => {
  const { id: assessmentId } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { addToast } = useToast();

  const [assessment, setAssessment] = useState(null);
  const [attempt, setAttempt] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Active question index
  const [currentIndex, setCurrentIndex] = useState(0);

  // Current answers dictionary: questionId -> { selectedOptionId, answerText }
  const [answers, setAnswers] = useState({});
  const [savingAnswer, setSavingAnswer] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [lastSavedTime, setLastSavedTime] = useState(null);

  // Timer state (seconds remaining)
  const [secondsRemaining, setSecondsRemaining] = useState(null);
  const [submitConfirmOpen, setSubmitConfirmOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Initialize: start or resume attempt
  const initializeAttempt = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      // Fetch assessment details
      const assRes = await api.get(`/assessments/${assessmentId}`);
      setAssessment(assRes.data);

      // Start or resume attempt
      const attRes = await api.post(`/assessments/${assessmentId}/start`);
      const currentAttempt = attRes.data;
      setAttempt(currentAttempt);

      // If attempt is already SUBMITTED or EVALUATED, redirect to results
      if (currentAttempt.status === 'SUBMITTED' || currentAttempt.status === 'EVALUATED') {
        navigate(`/attempts/${currentAttempt.id}/results`);
        return;
      }

      // Populate draft answers already saved on backend
      const existingAnswers = {};
      if (currentAttempt.answers) {
        for (const ans of currentAttempt.answers) {
          existingAnswers[ans.questionId] = {
            selectedOptionId: ans.selectedOptionId,
            answerText: ans.answerText || '',
          };
        }
      }
      setAnswers(existingAnswers);

      // Calculate time remaining
      const durationSec = assRes.data.durationMinutes * 60;
      const startedAtMs = new Date(currentAttempt.startedAt).getTime();
      const elapsedSec = Math.floor((Date.now() - startedAtMs) / 1000);
      const remaining = Math.max(0, durationSec - elapsedSec);
      setSecondsRemaining(remaining);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to start or resume assessment.');
    } finally {
      setLoading(false);
    }
  }, [assessmentId, navigate]);

  useEffect(() => {
    initializeAttempt();
  }, [initializeAttempt]);

  // Countdown timer
  useEffect(() => {
    if (secondsRemaining === null || secondsRemaining <= 0) return;

    const timer = setInterval(() => {
      setSecondsRemaining((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          // Auto submit when time runs out
          handleFinalSubmit();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [secondsRemaining]);

  // Save single answer to backend
  const persistAnswer = async (questionId, optionId, text) => {
    if (!attempt) return;
    try {
      setSavingAnswer(true);
      setSaveError(null);
      await api.post(`/attempts/${attempt.id}/answers`, {
        questionId,
        selectedOptionId: optionId || undefined,
        answerText: text !== undefined ? text : undefined,
      });
      setLastSavedTime(new Date());
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to save answer. Your changes may not be persisted.';
      setSaveError(msg);
      addToast(msg, 'error');
    } finally {
      setSavingAnswer(false);
    }
  };

  const handleSelectOption = (questionId, optionId) => {
    const updated = {
      ...answers,
      [questionId]: {
        ...(answers[questionId] || {}),
        selectedOptionId: optionId,
      },
    };
    setAnswers(updated);
    persistAnswer(questionId, optionId, answers[questionId]?.answerText);
  };

  const handleTextAnswerChange = (questionId, text) => {
    const updated = {
      ...answers,
      [questionId]: {
        ...(answers[questionId] || {}),
        answerText: text,
      },
    };
    setAnswers(updated);
  };

  const handleTextAnswerBlur = (questionId) => {
    persistAnswer(questionId, answers[questionId]?.selectedOptionId, answers[questionId]?.answerText);
  };

  const handleFinalSubmit = async () => {
    if (!attempt) return;
    try {
      setSubmitting(true);
      const res = await api.post(`/attempts/${attempt.id}/submit`);
      addToast('Assessment submitted successfully!', 'success');
      navigate(`/attempts/${res.data.id}/results`);
    } catch (err) {
      addToast(err.response?.data?.message || 'Failed to submit assessment.', 'error');
      setSubmitting(false);
    }
  };

  const formatTimer = (totalSec) => {
    if (totalSec === null) return '--:--';
    const mins = Math.floor(totalSec / 60);
    const secs = totalSec % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  if (loading) {
    return (
      <div className="space-y-6 max-w-4xl mx-auto py-6">
        <Skeleton className="h-16 rounded-xl" />
        <Skeleton className="h-96 rounded-xl" />
      </div>
    );
  }

  if (error || !assessment) {
    return (
      <div className="max-w-2xl mx-auto py-12">
        <ErrorState message={error || 'Could not load assessment.'} onRetry={initializeAttempt} />
      </div>
    );
  }

  const questions = assessment.questions || [];
  const currentQ = questions[currentIndex];
  const answeredCount = Object.keys(answers).filter((qId) => {
    const a = answers[qId];
    return a && (a.selectedOptionId != null || (a.answerText && a.answerText.trim().length > 0));
  }).length;

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      {/* Top Banner Bar */}
      <div className="bg-slate-900 text-white px-6 py-4 rounded-xl shadow-md flex flex-wrap items-center justify-between gap-4 sticky top-4 z-20">
        <div>
          <span className="text-xs uppercase tracking-wider text-indigo-400 font-semibold">
            {assessment.classroomTitle}
          </span>
          <h1 className="text-lg font-bold tracking-tight">{assessment.title}</h1>
        </div>

        <div className="flex items-center gap-6">
          {/* Timer */}
          <div
            className={`flex items-center gap-2 px-3 py-1.5 rounded-lg font-mono font-bold text-sm ${
              secondsRemaining < 300 ? 'bg-red-950/80 text-red-400 animate-pulse' : 'bg-slate-800 text-slate-200'
            }`}
          >
            <Clock className="w-4 h-4" />
            <span>{formatTimer(secondsRemaining)}</span>
          </div>

          {/* Submit Button */}
          <Button
            variant="primary"
            size="sm"
            onClick={() => setSubmitConfirmOpen(true)}
            disabled={submitting}
          >
            <Send className="w-3.5 h-3.5 mr-1.5" />
            Submit Test
          </Button>
        </div>
      </div>

      {/* Progress & Question tracker */}
      <Card className="p-3">
        <div className="flex flex-wrap items-center justify-between gap-2 pb-2 border-b border-slate-100">
          <span className="text-xs font-semibold text-slate-700">
            Question Navigation ({answeredCount}/{questions.length} answered)
          </span>
          {savingAnswer ? (
            <span className="text-[11px] text-slate-400 flex items-center gap-1">
              <Loader2 className="w-3 h-3 animate-spin text-indigo-500" /> Saving answer...
            </span>
          ) : saveError ? (
            <span className="text-[11px] text-rose-600 flex items-center gap-1 font-medium">
              <AlertCircle className="w-3 h-3" /> Save failed
            </span>
          ) : lastSavedTime ? (
            <span className="text-[11px] text-emerald-600 flex items-center gap-1">
              <CheckCircle className="w-3 h-3" /> Autosaved
            </span>
          ) : null}
        </div>

        <div className="flex flex-wrap gap-2 pt-3">
          {questions.map((q, idx) => {
            const hasAnswer =
              answers[q.id]?.selectedOptionId != null ||
              (answers[q.id]?.answerText && answers[q.id]?.answerText.trim().length > 0);
            const isCurrent = idx === currentIndex;

            return (
              <button
                key={q.id}
                onClick={() => setCurrentIndex(idx)}
                className={`w-8 h-8 rounded-lg text-xs font-bold transition-all ${
                  isCurrent
                    ? 'ring-2 ring-indigo-600 bg-indigo-600 text-white'
                    : hasAnswer
                    ? 'bg-emerald-100 text-emerald-800 border border-emerald-300'
                    : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                }`}
              >
                {idx + 1}
              </button>
            );
          })}
        </div>
      </Card>

      {/* Active Question Card */}
      {currentQ ? (
        <Card className="p-6 space-y-6">
          <div className="flex items-center justify-between gap-2 pb-4 border-b border-slate-100">
            <div className="flex items-center gap-2">
              <span className="px-2.5 py-1 bg-indigo-50 text-indigo-700 rounded-lg text-xs font-bold">
                Question {currentIndex + 1} of {questions.length}
              </span>
              <span className="px-2 py-0.5 bg-slate-100 text-slate-600 rounded text-xs">
                {currentQ.questionType}
              </span>
            </div>
            <span className="text-xs font-bold text-slate-700">
              {currentQ.marks} {Number(currentQ.marks) === 1 ? 'Point' : 'Points'}
            </span>
          </div>

          <p className="text-base font-medium text-slate-900 leading-relaxed">
            {currentQ.questionText}
          </p>

          {/* MCQ Options */}
          {currentQ.questionType === 'MCQ' && currentQ.options && (
            <div className="space-y-2.5 pt-2">
              {currentQ.options.map((opt) => {
                const isSelected = answers[currentQ.id]?.selectedOptionId === opt.id;
                return (
                  <label
                    key={opt.id}
                    className={`flex items-center gap-3 p-3.5 rounded-xl border text-sm cursor-pointer transition-all ${
                      isSelected
                        ? 'border-indigo-600 bg-indigo-50/50 text-indigo-950 font-medium ring-1 ring-indigo-600'
                        : 'border-slate-200 hover:border-slate-300 bg-white text-slate-800'
                    }`}
                  >
                    <input
                      type="radio"
                      name={`question_${currentQ.id}`}
                      checked={isSelected}
                      onChange={() => handleSelectOption(currentQ.id, opt.id)}
                      className="w-4 h-4 text-indigo-600 focus:ring-indigo-500"
                    />
                    <span>{opt.optionText}</span>
                  </label>
                );
              })}
            </div>
          )}

          {/* Subjective Textarea */}
          {currentQ.questionType !== 'MCQ' && (
            <div className="space-y-2 pt-2">
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                Your Answer
              </label>
              <textarea
                value={answers[currentQ.id]?.answerText || ''}
                onChange={(e) => handleTextAnswerChange(currentQ.id, e.target.value)}
                onBlur={() => handleTextAnswerBlur(currentQ.id)}
                rows={6}
                placeholder="Type your explanation or response here..."
                className="w-full p-3.5 border border-slate-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 leading-relaxed"
              />
              <span className="text-[11px] text-slate-400">
                Click outside or move to the next question to autosave.
              </span>
            </div>
          )}

          {/* Bottom navigation buttons */}
          <div className="flex items-center justify-between pt-4 border-t border-slate-100">
            <Button
              variant="secondary"
              onClick={() => setCurrentIndex((prev) => Math.max(0, prev - 1))}
              disabled={currentIndex === 0}
            >
              <ArrowLeft className="w-4 h-4 mr-1.5" /> Previous
            </Button>

            {currentIndex < questions.length - 1 ? (
              <Button
                variant="primary"
                onClick={() => setCurrentIndex((prev) => Math.min(questions.length - 1, prev + 1))}
              >
                Next <ArrowRight className="w-4 h-4 ml-1.5" />
              </Button>
            ) : (
              <Button
                variant="primary"
                onClick={() => setSubmitConfirmOpen(true)}
              >
                Finish & Submit <Send className="w-4 h-4 ml-1.5" />
              </Button>
            )}
          </div>
        </Card>
      ) : null}

      {/* Confirmation Modal */}
      <Modal
        isOpen={submitConfirmOpen}
        onClose={() => setSubmitConfirmOpen(false)}
        title="Submit Assessment"
        maxWidth="max-w-md"
      >
        <div className="space-y-4">
          <p className="text-sm text-slate-700">
            You have answered <strong>{answeredCount}</strong> out of{' '}
            <strong>{questions.length}</strong> questions.
          </p>
          {answeredCount < questions.length && (
            <div className="flex items-center gap-2 p-3 bg-amber-50 border border-amber-200 rounded-lg text-amber-800 text-xs">
              <AlertTriangle className="w-4 h-4 flex-shrink-0 text-amber-600" />
              <span>You have unanswered questions. Are you sure you want to proceed?</span>
            </div>
          )}
          <p className="text-xs text-slate-500">
            Once submitted, your answers will be automatically evaluated by the server.
          </p>

          <div className="flex justify-end gap-2 pt-3 border-t border-slate-200">
            <Button
              variant="secondary"
              onClick={() => setSubmitConfirmOpen(false)}
              disabled={submitting}
            >
              Go Back
            </Button>
            <Button
              variant="primary"
              onClick={handleFinalSubmit}
              disabled={submitting}
            >
              {submitting ? 'Submitting...' : 'Yes, Submit Now'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
