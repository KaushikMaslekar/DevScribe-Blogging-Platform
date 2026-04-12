import { apiClient } from "@/lib/api-client";
import type {
  AutosavePostRequest,
  AutosavePostResponse,
  CreatePostRequest,
  PageResponse,
  PostDetail,
  PostStatus,
  PostSummary,
  UpdatePostRequest,
} from "@/types/post";

export async function listPosts(params?: {
  page?: number;
  size?: number;
  mine?: boolean;
  status?: PostStatus;
  tag?: string;
  query?: string;
}): Promise<PageResponse<PostSummary>> {
  const { data } = await apiClient.get<PageResponse<PostSummary>>("/posts", {
    params,
  });
  return data;
}

export async function getPostBySlug(slug: string): Promise<PostDetail> {
  const { data } = await apiClient.get<PostDetail>(`/posts/${slug}`);
  return data;
}

export async function createPost(
  payload: CreatePostRequest,
): Promise<PostDetail> {
  const { data } = await apiClient.post<PostDetail>("/posts", payload);
  return data;
}

export async function updatePost(
  id: number,
  payload: UpdatePostRequest,
): Promise<PostDetail> {
  const { data } = await apiClient.put<PostDetail>(`/posts/${id}`, payload);
  return data;
}

export async function deletePost(id: number): Promise<void> {
  await apiClient.delete(`/posts/${id}`);
}

export async function publishPost(id: number): Promise<PostDetail> {
  const { data } = await apiClient.post<PostDetail>(`/posts/${id}/publish`);
  return data;
}

export async function autosavePost(
  payload: AutosavePostRequest,
): Promise<AutosavePostResponse> {
  const { data } = await apiClient.post<AutosavePostResponse>(
    "/posts/autosave",
    payload,
  );
  return data;
}
