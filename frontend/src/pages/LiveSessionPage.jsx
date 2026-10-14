import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Client } from '@stomp/stompjs';
import { useAuth } from '../context/AuthContext';
import { api, getAccessToken } from '../api/client';
import { Button } from '../components/common/Button';
import { Modal } from '../components/common/Modal';
import { Skeleton } from '../components/common/Skeleton';
import { DiscussionPanel } from '../components/session/DiscussionPanel';
import { PollPanel } from '../components/session/PollPanel';
import {
  ArrowLeft,
  Video,
  AlertTriangle,
  Users,
  RefreshCw,
  Clock,
  ShieldAlert,
  MessageSquare,
  BarChart2,
  Wifi,
  WifiOff,
  PanelRightClose,
  PanelRightOpen,
} from 'lucide-react';

export const LiveSessionPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();

  const [session, setSession] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sessionError, setSessionError] = useState(null);

  // Jitsi connection state
  const [jitsiData, setJitsiData] = useState(null);
  const [integrationNotConfigured, setIntegrationNotConfigured] = useState(null);
  const [joining, setJoining] = useState(true);

  // Side panel tabs: 'chat' | 'polls'
  const [activeTab, setActiveTab] = useState('chat');
  const [panelOpen, setPanelOpen] = useState(true);

  // Real STOMP WebSocket client state
  const [stompClient, setStompClient] = useState(null);
  const [connectionState, setConnectionState] = useState('CONNECTING'); // 'CONNECTING' | 'CONNECTED' | 'DISCONNECTED'
  const [connectionError, setConnectionError] = useState(null);

  // Attendance modal (Teacher/Admin)
  const [attendanceModalOpen, setAttendanceModalOpen] = useState(false);
  const [attendanceList, setAttendanceList] = useState([]);
  const [loadingAttendance, setLoadingAttendance] = useState(false);
  const [attendanceError, setAttendanceError] = useState(null);

  const jitsiContainerRef = useRef(null);
  const jitsiApiRef = useRef(null);
  const clientRef = useRef(null);

  const isTeacherOrAdmin = user?.role === 'TEACHER' || user?.role === 'ADMIN';

  // Fetch session metadata
  const fetchSession = useCallback(async () => {
    try {
      const res = await api.get(`/sessions/${id}`);
      setSession(res.data);
    } catch (err) {
      setSessionError(err.response?.data?.message || 'Failed to load session details');
    }
  }, [id]);

  // Request join and Jitsi JWT
  const handleJoin = useCallback(async () => {
    setJoining(true);
    setIntegrationNotConfigured(null);
    try {
      const res = await api.post(`/sessions/${id}/join`);
      setJitsiData(res.data);
    } catch (err) {
      if (err.response?.status === 503 || err.response?.data?.code === 'INTEGRATION_NOT_CONFIGURED') {
        setIntegrationNotConfigured(err.response?.data?.message || 'Jitsi integration is not configured on this server.');
      } else {
        setSessionError(err.response?.data?.message || 'Failed to join live session.');
      }
    } finally {
      setJoining(false);
      setLoading(false);
    }
  }, [id]);

  // Leave session handler
  const handleLeaveSession = useCallback(async () => {
    try {
      await api.post(`/sessions/${id}/leave`);
    } catch (err) {
      console.error('Failed to record session leave:', err);
    }
    navigate('/sessions');
  }, [id, navigate]);

  useEffect(() => {
    fetchSession();
    handleJoin();
  }, [fetchSession, handleJoin]);

  // Periodic attendance heartbeat and leave on unload
  useEffect(() => {
    const heartbeatInterval = setInterval(() => {
      api.post(`/sessions/${id}/attendance/heartbeat`).catch((err) => {
        console.error('Attendance heartbeat error:', err);
      });
    }, 30000);

    const handleBeforeUnload = () => {
      // Beacon or leave notification
      navigator.sendBeacon?.(`/api/v1/sessions/${id}/leave`);
    };
    window.addEventListener('beforeunload', handleBeforeUnload);

    return () => {
      clearInterval(heartbeatInterval);
      window.removeEventListener('beforeunload', handleBeforeUnload);
      // Record leave on unmount
      try {
        const leavePromise = api.post(`/sessions/${id}/leave`);
        if (leavePromise && typeof leavePromise.catch === 'function') {
          leavePromise.catch((err) => {
            console.warn('Session leave notification failed:', err);
          });
        }
      } catch (e) {
        console.warn('Could not dispatch leave request on unmount:', e);
      }
    };
  }, [id]);

  // Establish STOMP connection
  const connectStomp = useCallback(() => {
    const token = getAccessToken();
    if (!token) return;

    setConnectionState('CONNECTING');
    setConnectionError(null);

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const brokerURL = `${protocol}//${window.location.host}/ws`;

    const client = new Client({
      brokerURL,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setConnectionState('CONNECTED');
        setConnectionError(null);
      },
      onStompError: (frame) => {
        console.error('STOMP broker error:', frame);
        setConnectionState('DISCONNECTED');
        setConnectionError(frame.headers['message'] || 'Connection error with real-time server');
      },
      onWebSocketClose: () => {
        setConnectionState('DISCONNECTED');
      },
      onWebSocketError: (event) => {
        console.error('WebSocket connection error:', event);
        setConnectionState('DISCONNECTED');
      },
    });

    client.activate();
    clientRef.current = client;
    setStompClient(client);
  }, []);

  useEffect(() => {
    connectStomp();

    return () => {
      if (clientRef.current) {
        clientRef.current.deactivate();
        clientRef.current = null;
      }
    };
  }, [connectStomp]);

  // Mount Jitsi Meet when jitsiData is ready
  useEffect(() => {
    if (!jitsiData || !jitsiContainerRef.current) return;

    const initJitsi = () => {
      if (!window.JitsiMeetExternalAPI) return;

      if (jitsiApiRef.current) {
        jitsiApiRef.current.dispose();
      }

      const domain = jitsiData.domain || '8x8.vc';
      const options = {
        roomName: jitsiData.roomName,
        jwt: jitsiData.token,
        parentNode: jitsiContainerRef.current,
        width: '100%',
        height: '100%',
        configOverwrite: {
          startWithAudioMuted: false,
          startWithVideoMuted: false,
        },
        interfaceConfigOverwrite: {
          SHOW_JITSI_WATERMARK: false,
        },
      };

      jitsiApiRef.current = new window.JitsiMeetExternalAPI(domain, options);
    };

    if (window.JitsiMeetExternalAPI) {
      initJitsi();
    } else {
      const script = document.createElement('script');
      script.src = 'https://8x8.vc/external_api.js';
      script.async = true;
      script.onload = initJitsi;
      document.body.appendChild(script);
    }

    return () => {
      if (jitsiApiRef.current) {
        jitsiApiRef.current.dispose();
        jitsiApiRef.current = null;
      }
    };
  }, [jitsiData]);

  // Fetch Attendance
  const fetchAttendance = async () => {
    setLoadingAttendance(true);
    setAttendanceError(null);
    setAttendanceModalOpen(true);
    try {
      const res = await api.get(`/sessions/${id}/attendance`);
      setAttendanceList(res.data || []);
    } catch (err) {
      setAttendanceError(err.response?.data?.message || 'Failed to load session attendance');
    } finally {
      setLoadingAttendance(false);
    }
  };

  const formatDuration = (seconds) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}m ${secs}s`;
  };

  const isUserActive = (lastSeenAt) => {
    if (!lastSeenAt) return false;
    const diffSeconds = (new Date().getTime() - new Date(lastSeenAt).getTime()) / 1000;
    return diffSeconds < 120; // Seen within last 2 minutes
  };

  return (
    <div className="flex flex-col h-[calc(100vh-6rem)]">
      {/* Session Top Bar */}
      <div className="bg-white border-b border-slate-200 px-6 py-3 flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-4">
          <button
            onClick={handleLeaveSession}
            className="text-xs font-semibold text-slate-500 hover:text-rose-600 flex items-center gap-1 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" /> Leave Session
          </button>
          <div className="h-4 w-px bg-slate-200" />
          <div>
            <h1 className="text-base font-bold text-slate-900 leading-tight">
              {session?.title || 'Live Video Session'}
            </h1>
            <p className="text-xs text-slate-500 mt-0.5">{session?.classroomName}</p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          {/* Real-time Connection State Indicator */}
          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-medium border">
            {connectionState === 'CONNECTED' ? (
              <span className="flex items-center gap-1 text-emerald-600 border-emerald-200 bg-emerald-50">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                Live STOMP
              </span>
            ) : connectionState === 'CONNECTING' ? (
              <span className="flex items-center gap-1 text-amber-600 border-amber-200 bg-amber-50">
                <RefreshCw className="w-3 h-3 animate-spin text-amber-500" />
                Connecting...
              </span>
            ) : (
              <span className="flex items-center gap-1 text-rose-600 border-rose-200 bg-rose-50">
                <WifiOff className="w-3 h-3 text-rose-500" />
                Disconnected
                <button
                  onClick={connectStomp}
                  className="ml-1 underline font-semibold hover:text-rose-800"
                >
                  Retry
                </button>
              </span>
            )}
          </div>

          {isTeacherOrAdmin && (
            <Button size="sm" variant="secondary" icon={Users} onClick={fetchAttendance}>
              Attendance
            </Button>
          )}

          {/* Toggle Panel Button */}
          <button
            onClick={() => setPanelOpen(!panelOpen)}
            className="p-1.5 text-slate-500 hover:text-slate-800 rounded-lg hover:bg-slate-100 transition-colors"
            title={panelOpen ? 'Collapse side panel' : 'Open side panel'}
          >
            {panelOpen ? <PanelRightClose className="w-5 h-5" /> : <PanelRightOpen className="w-5 h-5" />}
          </button>
        </div>
      </div>

      {/* Main Workspace (Split Video + Side Panel) */}
      <div className="flex-1 flex overflow-hidden bg-slate-950">
        {/* Left: Video Conference Stage */}
        <div className="flex-1 relative overflow-hidden flex flex-col items-center justify-center">
          {joining || loading ? (
            <div className="text-center p-8">
              <RefreshCw className="w-8 h-8 text-indigo-500 animate-spin mx-auto mb-3" />
              <p className="text-sm font-semibold text-white">Connecting to secure video conference...</p>
              <p className="text-xs text-slate-400 mt-1">Verifying room permissions and issuing session tokens</p>
            </div>
          ) : integrationNotConfigured ? (
            /* Real 503 INTEGRATION_NOT_CONFIGURED state strictly following AGENTS.md rule 1 and 5 */
            <div className="max-w-xl mx-auto p-8 bg-slate-900 rounded-2xl border border-slate-800 text-center shadow-2xl m-4">
              <div className="w-14 h-14 rounded-2xl bg-amber-500/10 text-amber-400 flex items-center justify-center mx-auto mb-4 border border-amber-500/20">
                <ShieldAlert className="w-7 h-7" />
              </div>

              <h2 className="text-xl font-bold text-white">Live Video Integration Not Configured</h2>
              <p className="text-xs text-slate-400 mt-2 leading-relaxed">
                HTTP 503 INTEGRATION_NOT_CONFIGURED: As mandated by project rules, mock video or public fallbacks are strictly prohibited.
              </p>

              <div className="mt-4 p-4 rounded-xl bg-slate-950 border border-slate-800 text-left">
                <p className="text-[11px] font-mono text-amber-300 break-words leading-relaxed">
                  {integrationNotConfigured}
                </p>
                <div className="mt-3 pt-3 border-t border-slate-800 text-xs text-slate-400 space-y-1">
                  <p className="font-semibold text-slate-300">Required environment variables:</p>
                  <p className="font-mono text-[11px] text-slate-400">• JITSI_APP_ID (8x8 JaaS App ID)</p>
                  <p className="font-mono text-[11px] text-slate-400">• JITSI_KID (Key ID)</p>
                  <p className="font-mono text-[11px] text-slate-400">• JITSI_PRIVATE_KEY_PATH (Path to RSA PEM private key)</p>
                </div>
              </div>

              <div className="mt-6 flex items-center justify-center gap-3">
                <Button variant="secondary" onClick={handleLeaveSession}>
                  Leave Session
                </Button>
                <Button variant="primary" icon={RefreshCw} onClick={handleJoin}>
                  Retry Join
                </Button>
              </div>
            </div>
          ) : sessionError ? (
            <div className="max-w-md mx-auto p-6 bg-slate-900 rounded-2xl border border-rose-900/50 text-center m-4">
              <AlertTriangle className="w-8 h-8 text-rose-500 mx-auto mb-2" />
              <h3 className="text-base font-bold text-white">Unable to Join Session</h3>
              <p className="text-xs text-slate-400 mt-1">{sessionError}</p>
              <div className="mt-4 flex justify-center gap-3">
                <Button variant="secondary" onClick={handleLeaveSession}>
                  Back
                </Button>
                <Button variant="primary" onClick={handleJoin}>
                  Retry
                </Button>
              </div>
            </div>
          ) : (
            /* Jitsi Embed Container */
            <div ref={jitsiContainerRef} className="w-full h-full" />
          )}
        </div>

        {/* Right: Collapsible Real-Time Panel (Chat & Polls) */}
        {panelOpen && (
          <div className="w-80 md:w-96 flex flex-col h-full bg-slate-900 border-l border-slate-800 flex-shrink-0">
            {/* Panel Tab Selector */}
            <div className="flex border-b border-slate-800 bg-slate-950 px-2 pt-2 gap-1 flex-shrink-0">
              <button
                onClick={() => setActiveTab('chat')}
                className={`flex-1 py-2 text-xs font-semibold rounded-t-lg flex items-center justify-center gap-1.5 transition-colors border-t border-x ${
                  activeTab === 'chat'
                    ? 'bg-slate-900 text-white border-slate-800'
                    : 'text-slate-400 hover:text-slate-200 border-transparent bg-transparent'
                }`}
              >
                <MessageSquare className="w-3.5 h-3.5" />
                Live Chat
              </button>

              <button
                onClick={() => setActiveTab('polls')}
                className={`flex-1 py-2 text-xs font-semibold rounded-t-lg flex items-center justify-center gap-1.5 transition-colors border-t border-x ${
                  activeTab === 'polls'
                    ? 'bg-slate-900 text-white border-slate-800'
                    : 'text-slate-400 hover:text-slate-200 border-transparent bg-transparent'
                }`}
              >
                <BarChart2 className="w-3.5 h-3.5" />
                Polls
              </button>
            </div>

            {/* Panel Tab Content */}
            <div className="flex-1 overflow-hidden">
              {activeTab === 'chat' ? (
                <DiscussionPanel
                  sessionId={id}
                  stompClient={stompClient}
                  isConnected={connectionState === 'CONNECTED'}
                  user={user}
                />
              ) : (
                <PollPanel
                  sessionId={id}
                  stompClient={stompClient}
                  isConnected={connectionState === 'CONNECTED'}
                  user={user}
                  isTeacherOrAdmin={isTeacherOrAdmin}
                />
              )}
            </div>
          </div>
        )}
      </div>

      {/* Attendance Modal */}
      <Modal isOpen={attendanceModalOpen} onClose={() => setAttendanceModalOpen(false)} title="Session Attendance Log">
        <div className="space-y-4">
          <p className="text-xs text-slate-500">
            Real-time client-reported attendance recorded on join, updated via client heartbeats and leave events.
          </p>

          {loadingAttendance ? (
            <div className="space-y-2">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : attendanceError ? (
            <div className="py-6 text-center text-xs text-rose-600 space-y-2">
              <p>{attendanceError}</p>
              <Button size="sm" variant="secondary" onClick={fetchAttendance}>
                Retry
              </Button>
            </div>
          ) : attendanceList.length === 0 ? (
            <div className="py-6 text-center text-xs text-slate-400">
              No attendance entries recorded yet for this session.
            </div>
          ) : (
            <div className="divide-y divide-slate-100 max-h-80 overflow-y-auto">
              {attendanceList.map((att) => {
                const active = isUserActive(att.lastSeenAt);

                return (
                  <div key={att.id} className="py-2.5 flex items-center justify-between text-xs">
                    <div className="flex items-center gap-2.5">
                      <span
                        className={`w-2 h-2 rounded-full ${active ? 'bg-emerald-500' : 'bg-slate-300'}`}
                        title={active ? 'Currently active' : 'Left session'}
                      />
                      <div>
                        <div className="flex items-center gap-1.5">
                          <p className="font-semibold text-slate-900">{att.userName}</p>
                          <span
                            className={`text-[9px] px-1.5 py-0.2 rounded font-medium ${
                              active
                                ? 'bg-emerald-50 text-emerald-600 border border-emerald-200'
                                : 'bg-slate-100 text-slate-500'
                            }`}
                          >
                            {active ? 'Active' : 'Offline'}
                          </span>
                        </div>
                        <p className="text-slate-500 text-[11px]">{att.userEmail}</p>
                      </div>
                    </div>
                    <div className="text-right">
                      <p className="font-mono font-semibold text-slate-700">
                        {formatDuration(att.durationSeconds)}
                      </p>
                      <p className="text-[10px] text-slate-400">
                        Last seen: {new Date(att.lastSeenAt).toLocaleTimeString()}
                      </p>
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          <div className="flex justify-between items-center pt-2 border-t border-slate-100">
            <Button size="sm" variant="secondary" icon={RefreshCw} onClick={fetchAttendance}>
              Refresh Log
            </Button>
            <Button variant="secondary" onClick={() => setAttendanceModalOpen(false)}>
              Close
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
