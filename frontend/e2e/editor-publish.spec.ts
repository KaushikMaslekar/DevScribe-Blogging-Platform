import { expect, test, type APIRequestContext } from "@playwright/test";

type AuthResponse = {
  accessToken: string;
  expiresInMs: number;
};

type PostDetailResponse = {
  id: number;
  slug: string;
  title: string;
  status: "DRAFT" | "PUBLISHED";
};

const API_BASE_URL = process.env.E2E_API_URL ?? "http://localhost:8080/api";

function uniqueSuffix() {
  return `${Date.now()}-${Math.floor(Math.random() * 10000)}`;
}

async function registerAndGetToken(request: APIRequestContext) {
  const suffix = uniqueSuffix();
  const email = `e2e-publish-${suffix}@example.com`;
  const username = `e2e_publish_${suffix}`;

  const response = await request.post(`${API_BASE_URL}/auth/register`, {
    data: {
      email,
      username,
      password: "StrongPassword123!",
    },
  });

  expect(response.ok()).toBeTruthy();
  const payload = (await response.json()) as AuthResponse;
  return payload.accessToken;
}

test("create and publish post workflow", async ({ request, page }) => {
  const token = await registerAndGetToken(request);
  const suffix = uniqueSuffix();

  const createResponse = await request.post(`${API_BASE_URL}/posts`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
    data: {
      title: `E2E Publish ${suffix}`,
      excerpt: "E2E excerpt",
      markdownContent: "# E2E Title\n\nE2E content body.",
      tags: ["e2e", "release"],
    },
  });

  expect(createResponse.ok()).toBeTruthy();
  const created = (await createResponse.json()) as PostDetailResponse;

  const publishResponse = await request.post(
    `${API_BASE_URL}/posts/${created.id}/publish`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    },
  );

  expect(publishResponse.ok()).toBeTruthy();
  const published = (await publishResponse.json()) as PostDetailResponse;
  expect(published.status).toBe("PUBLISHED");

  const detailResponse = await request.get(
    `${API_BASE_URL}/posts/${published.slug}`,
  );
  expect(detailResponse.ok()).toBeTruthy();

  await page.goto(`/posts/${published.slug}`);
  await expect(page).toHaveTitle(/DevScribe|E2E/i);
});
