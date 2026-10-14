import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../api/client';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Input } from '../components/common/Input';
import { Modal } from '../components/common/Modal';
import { Skeleton } from '../components/common/Skeleton';
import { ErrorState } from '../components/common/ErrorState';
import { useToast } from '../components/common/Toast';
import {
  Users,
  BookOpen,
  Calendar,
  FileText,
  ShieldAlert,
  Search,
  CheckCircle2,
  AlertCircle,
  UserX,
  UserCheck,
  GraduationCap,
  Sparkles,
} from 'lucide-react';

export const AdminPage = () => {
  const { user: currentAdmin } = useAuth();
  const { addToast } = useToast();

  // Platform stats
  const [stats, setStats] = useState(null);
  const [loadingStats, setLoadingStats] = useState(true);
  const [statsError, setStatsError] = useState(null);

  // Integrations status
  const [integrations, setIntegrations] = useState(null);
  const [loadingIntegrations, setLoadingIntegrations] = useState(true);
  const [integrationsError, setIntegrationsError] = useState(null);

  // Users list
  const [users, setUsers] = useState([]);
  const [loadingUsers, setLoadingUsers] = useState(true);
  const [usersError, setUsersError] = useState(null);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  // Status toggle confirmation modal
  const [confirmModalOpen, setConfirmModalOpen] = useState(false);
  const [targetUser, setTargetUser] = useState(null);
  const [submittingStatus, setSubmittingStatus] = useState(false);

  const fetchStats = useCallback(async () => {
    setLoadingStats(true);
    setStatsError(null);
    try {
      const res = await api.get('/admin/stats');
      setStats(res.data);
    } catch (err) {
      setStatsError(err.response?.data?.message || 'Failed to load platform statistics');
    } finally {
      setLoadingStats(false);
    }
  }, []);

  const fetchIntegrations = useCallback(async () => {
    setLoadingIntegrations(true);
    setIntegrationsError(null);
    try {
      const res = await api.get('/system/integrations');
      setIntegrations(res.data);
    } catch (err) {
      setIntegrationsError(err.response?.data?.message || 'Failed to load system integrations');
    } finally {
      setLoadingIntegrations(false);
    }
  }, []);

  const fetchUsers = useCallback(async () => {
    setLoadingUsers(true);
    setUsersError(null);
    try {
      let query = `/admin/users?page=${page}&size=20`;
      if (search.trim()) query += `&search=${encodeURIComponent(search.trim())}`;
      if (roleFilter) query += `&role=${roleFilter}`;

      const res = await api.get(query);
      setUsers(res.data.content || []);
      setTotalPages(res.data.totalPages || 1);
    } catch (err) {
      setUsersError(err.response?.data?.message || 'Failed to load admin users');
    } finally {
      setLoadingUsers(false);
    }
  }, [page, search, roleFilter]);

  useEffect(() => {
    fetchStats();
    fetchIntegrations();
  }, [fetchStats, fetchIntegrations]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const handleOpenConfirm = (userToToggle) => {
    setTargetUser(userToToggle);
    setConfirmModalOpen(true);
  };

  const handleConfirmStatusToggle = async () => {
    if (!targetUser) return;
    setSubmittingStatus(true);
    try {
      const newStatus = !targetUser.enabled;
      await api.patch(`/admin/users/${targetUser.id}/status`, { enabled: newStatus });
      addToast(
        `User ${targetUser.fullName} has been ${newStatus ? 'activated' : 'deactivated'}.`,
        'success'
      );
      setConfirmModalOpen(false);
      setTargetUser(null);
      fetchUsers();
      fetchStats();
    } catch (err) {
      addToast(err.response?.data?.message || 'Failed to update user status', 'error');
    } finally {
      setSubmittingStatus(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Platform Administration</h1>
        <p className="text-sm text-slate-500 mt-1">
          Monitor system metrics, manage user accounts, and verify external service integrations
        </p>
      </div>

      {/* Platform Stats Grid */}
      {loadingStats ? (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
        </div>
      ) : statsError ? (
        <ErrorState title="Stats Error" message={statsError} onRetry={fetchStats} />
      ) : stats ? (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <StatBox label="Total Users" value={stats.totalUsers} sub={`${stats.totalStudents} students, ${stats.totalTeachers} teachers`} />
          <StatBox label="Classrooms" value={stats.totalClassrooms} sub={`${stats.activeClassrooms} active`} />
          <StatBox label="Live Sessions" value={stats.totalSessions} sub="Recorded sessions" />
          <StatBox label="Enrollments" value={stats.totalEnrollments} sub={`${stats.totalMaterials} materials`} />
        </div>
      ) : null}

      {/* System Integrations Card */}
      <Card title="External System Integrations" subtitle="Backend provider connectivity">
        {loadingIntegrations ? (
          <Skeleton className="h-16 w-full" />
        ) : integrationsError ? (
          <ErrorState message={integrationsError} onRetry={fetchIntegrations} />
        ) : integrations ? (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <IntegrationItem name="Google Gemini" status={integrations.gemini} desc="AI quiz generation from PDFs" />
            <IntegrationItem name="Groq LLM" status={integrations.groq} desc="Fast text inference" />
            <IntegrationItem name="Jitsi Meet (JaaS)" status={integrations.jitsi} desc="Live video classroom (RS256 JWT)" />
          </div>
        ) : null}
      </Card>

      {/* User Management Section */}
      <Card title="User Directory & Access Control" subtitle="Search and manage student, teacher, and admin accounts">
        <div className="space-y-4">
          {/* Filters */}
          <div className="flex flex-col sm:flex-row gap-3">
            <div className="flex-1 relative">
              <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
              <input
                type="text"
                className="w-full pl-10 pr-4 py-2 text-sm rounded-xl border border-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                placeholder="Search by name or email..."
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(0);
                }}
              />
            </div>
            <select
              value={roleFilter}
              onChange={(e) => {
                setRoleFilter(e.target.value);
                setPage(0);
              }}
              className="px-3.5 py-2 text-sm rounded-xl border border-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
            >
              <option value="">All Roles</option>
              <option value="STUDENT">Student</option>
              <option value="TEACHER">Teacher</option>
              <option value="ADMIN">Admin</option>
            </select>
          </div>

          {/* Users Table */}
          {loadingUsers ? (
            <div className="space-y-2">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : usersError ? (
            <ErrorState message={usersError} onRetry={fetchUsers} />
          ) : users.length === 0 ? (
            <div className="py-8 text-center text-xs text-slate-400">
              No users found matching current filters.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-slate-200 text-slate-400 uppercase tracking-wider font-semibold">
                    <th className="py-3 px-3">User</th>
                    <th className="py-3 px-3">Role</th>
                    <th className="py-3 px-3">Status</th>
                    <th className="py-3 px-3">Created</th>
                    <th className="py-3 px-3 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {users.map((u) => {
                    const isSelf = u.id === currentAdmin?.id;
                    return (
                      <tr key={u.id} className="hover:bg-slate-50/60 transition-colors">
                        <td className="py-3 px-3">
                          <p className="font-semibold text-slate-900">{u.fullName}</p>
                          <p className="text-slate-500 text-[11px]">{u.email}</p>
                        </td>
                        <td className="py-3 px-3">
                          <span
                            className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                              u.role === 'ADMIN'
                                ? 'bg-purple-50 text-purple-700 border border-purple-200'
                                : u.role === 'TEACHER'
                                ? 'bg-blue-50 text-blue-700 border border-blue-200'
                                : 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                            }`}
                          >
                            {u.role}
                          </span>
                        </td>
                        <td className="py-3 px-3">
                          <span
                            className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                              u.enabled
                                ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                : 'bg-rose-50 text-rose-700 border border-rose-200'
                            }`}
                          >
                            {u.enabled ? 'Active' : 'Deactivated'}
                          </span>
                        </td>
                        <td className="py-3 px-3 text-slate-400">
                          {new Date(u.createdAt).toLocaleDateString()}
                        </td>
                        <td className="py-3 px-3 text-right">
                          {isSelf ? (
                            <span className="text-[11px] text-slate-400 italic">Current User</span>
                          ) : (
                            <button
                              onClick={() => handleOpenConfirm(u)}
                              className={`px-2.5 py-1 rounded-lg text-xs font-semibold flex items-center gap-1.5 ml-auto transition-colors ${
                                u.enabled
                                  ? 'bg-rose-50 hover:bg-rose-100 text-rose-700'
                                  : 'bg-emerald-50 hover:bg-emerald-100 text-emerald-700'
                              }`}
                            >
                              {u.enabled ? <UserX className="w-3.5 h-3.5" /> : <UserCheck className="w-3.5 h-3.5" />}
                              {u.enabled ? 'Deactivate' : 'Activate'}
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between pt-2 text-xs text-slate-500">
              <span>Page {page + 1} of {totalPages}</span>
              <div className="flex gap-2">
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  Previous
                </Button>
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </div>
      </Card>

      {/* Confirmation Modal */}
      <Modal
        isOpen={confirmModalOpen}
        onClose={() => setConfirmModalOpen(false)}
        title={targetUser?.enabled ? 'Deactivate Account' : 'Activate Account'}
      >
        <div className="space-y-4">
          <p className="text-xs text-slate-600 leading-relaxed">
            {targetUser?.enabled
              ? `Are you sure you want to deactivate ${targetUser?.fullName} (${targetUser?.email})? They will immediately be logged out, their refresh tokens will be revoked, and they will not be able to log in or access any APIs.`
              : `Are you sure you want to reactivate ${targetUser?.fullName} (${targetUser?.email})? They will be allowed to log in and use the platform again.`}
          </p>

          <div className="flex justify-end gap-3 pt-2">
            <Button variant="secondary" onClick={() => setConfirmModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant={targetUser?.enabled ? 'danger' : 'primary'}
              loading={submittingStatus}
              onClick={handleConfirmStatusToggle}
            >
              {targetUser?.enabled ? 'Confirm Deactivation' : 'Confirm Activation'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};

const StatBox = ({ label, value, sub }) => (
  <div className="bg-white rounded-2xl border border-slate-200 p-4 shadow-sm">
    <p className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">{label}</p>
    <p className="text-2xl font-bold text-slate-900 mt-0.5">{value}</p>
    <p className="text-[11px] text-slate-400 mt-1">{sub}</p>
  </div>
);

const IntegrationItem = ({ name, status, desc }) => {
  const isConfigured = status === 'CONFIGURED';

  return (
    <div className="p-3.5 rounded-xl border border-slate-200 bg-slate-50/50 flex items-start justify-between">
      <div>
        <h4 className="text-xs font-bold text-slate-900">{name}</h4>
        <p className="text-[11px] text-slate-500 mt-0.5">{desc}</p>
      </div>
      <span
        className={`px-2 py-0.5 rounded-full text-[10px] font-bold border flex items-center gap-1 ${
          isConfigured
            ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
            : 'bg-amber-50 text-amber-700 border-amber-200'
        }`}
      >
        {isConfigured ? <CheckCircle2 className="w-3 h-3" /> : <AlertCircle className="w-3 h-3" />}
        {status}
      </span>
    </div>
  );
};
