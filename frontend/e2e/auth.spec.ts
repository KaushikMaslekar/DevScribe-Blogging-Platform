import { expect, test } from "@playwright/test";

type AuthRegisterResponse = {
  accessToken: string;
  tokenType: string;
  expiresInMs: number;
  user: {
    id: number;
    email: string;
    username: string;
  };
};

const API_BASE_URL = process.env.E2E_API_URL ?? "http://localhost:8080/api";

function uniqueSuffix() {
  return `${Date.now()}-${Math.floor(Math.random() * 10000)}`;
}

test("register and access dashboard with auth cookie", async ({
  page,
  request,
  context,
  baseURL,
}) => {
  const suffix = uniqueSuffix();
  const email = `e2e-${suffix}@example.com`;
  const username = `e2e_${suffix}`;

  const registerResponse = await request.post(`${API_BASE_URL}/auth/register`, {
    data: {
      email,
      username,
      password: "StrongPassword123!",
    },
  });

  expect(registerResponse.ok()).toBeTruthy();
  const payload = (await registerResponse.json()) as AuthRegisterResponse;

  await context.addCookies([
    {
      name: "devscribe_access_token",
      value: payload.accessToken,
      path: "/",
      domain: new URL(baseURL ?? "http://localhost:3000").hostname,
      httpOnly: false,
      secure: false,
      sameSite: "Lax",
      expires: Math.floor(Date.now() / 1000) + 3600,
    },
  ]);

  await page.goto("/dashboard");
  await expect(page).not.toHaveURL(/\/login/);
});
