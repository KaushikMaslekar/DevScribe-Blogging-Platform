import {
  createClient,
  type RealtimeChannel,
  type SupabaseClient,
} from "@supabase/supabase-js";

export type PostRealtimeEventType =
  | "CREATED"
  | "UPDATED"
  | "PUBLISHED"
  | "DELETED";

export interface PostRealtimeEvent {
  postId: number;
  slug: string;
  status: "DRAFT" | "PUBLISHED";
  eventType: PostRealtimeEventType;
  occurredAt: string;
}

let supabaseClient: SupabaseClient | null = null;

function getSupabaseClient(): SupabaseClient | null {
  if (typeof window === "undefined") {
    return null;
  }

  if (supabaseClient) {
    return supabaseClient;
  }

  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

  if (!supabaseUrl || !supabaseAnonKey) {
    return null;
  }

  supabaseClient = createClient(supabaseUrl, supabaseAnonKey);
  return supabaseClient;
}

function eventNameToType(eventName: string): PostRealtimeEventType | null {
  if (eventName === "post.created") {
    return "CREATED";
  }
  if (eventName === "post.updated") {
    return "UPDATED";
  }
  if (eventName === "post.published") {
    return "PUBLISHED";
  }
  if (eventName === "post.deleted") {
    return "DELETED";
  }

  return null;
}

export function subscribeToPostRealtime(
  onEvent: (event: PostRealtimeEvent) => void,
): (() => void) | null {
  const client = getSupabaseClient();
  if (!client) {
    return null;
  }

  const channelName =
    process.env.NEXT_PUBLIC_REALTIME_POST_CHANNEL ?? "public:posts";
  const channel = client.channel(channelName, {
    config: {
      broadcast: { self: false, ack: false },
    },
  });

  const eventNames = [
    "post.created",
    "post.updated",
    "post.published",
    "post.deleted",
  ];

  eventNames.forEach((eventName) => {
    channel.on("broadcast", { event: eventName }, ({ payload }) => {
      if (!payload || typeof payload !== "object") {
        return;
      }

      const record = payload as Record<string, unknown>;
      const postId = Number(record.postId);
      const slug = record.slug;
      const status = record.status;
      const occurredAt = record.occurredAt;
      const eventType = eventNameToType(eventName);

      if (
        !Number.isFinite(postId) ||
        typeof slug !== "string" ||
        typeof occurredAt !== "string" ||
        !eventType
      ) {
        return;
      }

      if (status !== "DRAFT" && status !== "PUBLISHED") {
        return;
      }

      onEvent({
        postId,
        slug,
        status,
        eventType,
        occurredAt,
      });
    });
  });

  channel.subscribe();

  return () => {
    void client.removeChannel(channel as RealtimeChannel);
  };
}
