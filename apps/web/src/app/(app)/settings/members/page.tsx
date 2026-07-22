"use client";

import { useState } from "react";
import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { api, problemMessage } from "@/lib/api";
import type { components } from "@cairn/api-client";
import { useMe, isAdmin } from "@/lib/auth";
import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { EmptyState } from "@/components/ui/EmptyState";
import { Skeleton } from "@/components/ui/Skeleton";
import { useToast } from "@/components/ui/Toast";
import styles from "../../page.module.css";

type User = components["schemas"]["UserResponse"];
type Invite = components["schemas"]["InviteResponse"];

/**
 * Build a token-only accept link against THIS origin, regardless of the
 * server's configured acceptUrl base. Keeps the web route (/invite/accept)
 * authoritative for the link the admin shares.
 */
function acceptLinkFrom(serverAcceptUrl: string): string {
  let token = "";
  try {
    token = new URL(serverAcceptUrl).searchParams.get("token") ?? "";
  } catch {
    const m = serverAcceptUrl.match(/[?&]token=([^&]+)/);
    token = m ? decodeURIComponent(m[1]) : "";
  }
  const origin =
    typeof window !== "undefined" ? window.location.origin : "";
  return `${origin}/invite/accept?token=${encodeURIComponent(token)}`;
}

export default function MembersPage() {
  const { data: me, isLoading: meLoading } = useMe();
  const qc = useQueryClient();
  const toast = useToast();
  const [inviteEmail, setInviteEmail] = useState("");
  // acceptLinks captured this session, keyed by invite id.
  const [acceptLinks, setAcceptLinks] = useState<Record<string, string>>({});

  const users = useQuery({
    queryKey: ["users"],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/users");
      if (error) throw error;
      return (data?.data ?? []) as User[];
    },
    enabled: isAdmin(me),
  });

  const invites = useQuery({
    queryKey: ["invites"],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/invites");
      if (error) throw error;
      return (data?.data ?? []) as Invite[];
    },
    enabled: isAdmin(me),
  });

  const patchUser = useMutation({
    mutationFn: async (vars: {
      id: string;
      body: { role?: string; active?: boolean };
    }) => {
      const { error, response } = await api.PATCH("/api/v1/users/{id}", {
        params: { path: { id: vars.id } },
        body: vars.body,
      });
      if (error || !response.ok) {
        throw error ?? new Error("Update failed");
      }
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["users"] });
      qc.invalidateQueries({ queryKey: ["me"] });
      toast.success("Member updated");
    },
    onError: (err) => {
      toast.error(problemMessage(err, "Could not update member."));
    },
  });

  const createInvite = useMutation({
    mutationFn: async (email: string) => {
      const { data, error, response } = await api.POST("/api/v1/invites", {
        body: { email },
      });
      if (error || !response.ok) throw error ?? new Error("Invite failed");
      return data?.data as
        | components["schemas"]["CreatedInviteResponse"]
        | undefined;
    },
    onSuccess: async (created) => {
      setInviteEmail("");
      await qc.invalidateQueries({ queryKey: ["invites"] });
      if (created?.id && created.acceptUrl) {
        const link = acceptLinkFrom(created.acceptUrl);
        setAcceptLinks((m) => ({ ...m, [created.id!]: link }));
        await copy(link);
        toast.success("Invite created — link copied to clipboard");
      } else {
        toast.success("Invite created");
      }
    },
    onError: (err) => {
      toast.error(problemMessage(err, "Could not create invite."));
    },
  });

  const revokeInvite = useMutation({
    mutationFn: async (id: string) => {
      const { error, response } = await api.DELETE("/api/v1/invites/{id}", {
        params: { path: { id } },
      });
      if (error || !response.ok) throw error ?? new Error("Revoke failed");
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["invites"] });
      toast.success("Invite revoked");
    },
    onError: (err) => {
      toast.error(problemMessage(err, "Could not revoke invite."));
    },
  });

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      /* clipboard may be unavailable (non-secure context) — no-op */
    }
  }

  if (meLoading) {
    return (
      <div className={styles.page}>
        <Skeleton height={28} width={200} />
      </div>
    );
  }

  if (!isAdmin(me)) {
    return (
      <div className={styles.page}>
        <div className={styles.panel}>
          <EmptyState
            icon="🔒"
            headline="Admins only"
            body="You need an admin role to manage members and invites."
          />
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h1 className={styles.pageTitle}>Members</h1>
          <p className={styles.pageSub}>
            Manage roles, activation, and invitations.
          </p>
        </div>
      </div>

      {/* Users */}
      <div className={styles.panel}>
        <div className={styles.panelHeader}>
          <span className={styles.panelTitle}>People</span>
        </div>
        {users.isLoading ? (
          <div style={{ padding: 16 }}>
            <Skeleton height={20} />
          </div>
        ) : (
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th style={{ width: 160 }}>Role</th>
                <th style={{ width: 140 }}>Status</th>
              </tr>
            </thead>
            <tbody>
              {(users.data ?? []).map((u) => {
                const busy =
                  patchUser.isPending && patchUser.variables?.id === u.id;
                return (
                  <tr key={u.id}>
                    <td>
                      <span className={styles.nameCell}>
                        <Avatar name={u.name ?? "?"} seed={u.id} size={24} />
                        {u.name}
                      </span>
                    </td>
                    <td className={styles.muted}>{u.email}</td>
                    <td>
                      <Select
                        aria-label={`Role for ${u.name}`}
                        value={u.role}
                        disabled={busy}
                        onChange={(e) =>
                          patchUser.mutate({
                            id: u.id!,
                            body: { role: e.target.value },
                          })
                        }
                        options={[
                          { value: "admin", label: "Admin" },
                          { value: "member", label: "Member" },
                        ]}
                      />
                    </td>
                    <td>
                      <div className={styles.cellActions}>
                        <Button
                          variant={u.active ? "secondary" : "primary"}
                          size="compact"
                          loading={busy}
                          onClick={() =>
                            patchUser.mutate({
                              id: u.id!,
                              body: { active: !u.active },
                            })
                          }
                        >
                          {u.active ? "Deactivate" : "Activate"}
                        </Button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* Invites */}
      <div className={styles.panel}>
        <div className={styles.panelHeader}>
          <span className={styles.panelTitle}>Invites</span>
        </div>
        <form
          className={styles.inviteForm}
          onSubmit={(e) => {
            e.preventDefault();
            if (inviteEmail.trim()) createInvite.mutate(inviteEmail.trim());
          }}
        >
          <Input
            label="Invite by email"
            type="email"
            placeholder="teammate@company.com"
            value={inviteEmail}
            onChange={(e) => setInviteEmail(e.target.value)}
            required
          />
          <Button type="submit" loading={createInvite.isPending}>
            Send invite
          </Button>
        </form>

        {invites.isLoading ? (
          <div style={{ padding: 16 }}>
            <Skeleton height={20} />
          </div>
        ) : (invites.data ?? []).length === 0 ? (
          <EmptyState
            headline="No pending invites"
            body="Invite teammates by email; each gets a one-time join link."
          />
        ) : (
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Email</th>
                <th style={{ width: 160 }}>Expires</th>
                <th style={{ width: 220 }} />
              </tr>
            </thead>
            <tbody>
              {(invites.data ?? []).map((inv) => {
                const link = acceptLinks[inv.id ?? ""];
                const busy =
                  revokeInvite.isPending &&
                  revokeInvite.variables === inv.id;
                return (
                  <tr key={inv.id}>
                    <td>{inv.email}</td>
                    <td className={styles.muted}>
                      {inv.expiresAt
                        ? new Date(inv.expiresAt).toLocaleDateString()
                        : ""}
                    </td>
                    <td>
                      <div className={styles.cellActions}>
                        {link && (
                          <Button
                            variant="ghost"
                            size="compact"
                            onClick={() => {
                              copy(link);
                              toast.success("Link copied");
                            }}
                          >
                            Copy link
                          </Button>
                        )}
                        <Button
                          variant="ghost"
                          size="compact"
                          loading={busy}
                          onClick={() => revokeInvite.mutate(inv.id!)}
                        >
                          Revoke
                        </Button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
