import { test, expect } from '@playwright/test';

test('assembleia completa: criar, abrir, votar, impedir repetição e recuperar resultado', async ({
  page,
}, testInfo) => {
  const title = `Assembleia ${testInfo.project.name} ${Date.now()}`;
  const associate = `associado-${Date.now()}`;
  const browserErrors: string[] = [];
  page.on('pageerror', (error) => browserErrors.push(error.message));

  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await page.getByRole('button', { name: 'Nova pauta', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Título da pauta').fill(title);
  await dialog.getByLabel('Descrição').fill('Decisão criada pelo teste real de navegador.');
  const creation = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' && /\/api\/v1\/pautas$/.test(response.url()),
  );
  await dialog.getByRole('button', { name: 'Criar pauta', exact: true }).click();
  const created = await creation;
  expect(created.status()).toBe(201);
  const agenda = await created.json();
  await expect(dialog).not.toBeVisible();
  await expect(page.getByRole('heading', { name: title, exact: true, level: 2 })).toBeVisible();

  await page.getByLabel('Duração em minutos').fill('5');
  const opening = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/pautas/${agenda.id}/sessoes`),
  );
  await page.getByRole('button', { name: 'Abrir votação' }).click();
  expect((await opening).status()).toBe(201);
  await expect(page.getByRole('heading', { name: 'Registre seu voto' })).toBeVisible();

  await page.getByLabel('Identificação do associado').fill(associate);
  await page.getByRole('radio', { name: /^Sim/ }).check();
  const vote = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/pautas/${agenda.id}/votos`),
  );
  await page.getByRole('button', { name: 'Confirmar voto' }).click();
  expect((await vote).status()).toBe(201);
  await expect(page.getByText(`Voto de ${associate} registrado com sucesso.`)).toBeVisible();

  const duplicate = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/pautas/${agenda.id}/votos`),
  );
  await page.getByRole('button', { name: 'Confirmar voto' }).click();
  expect((await duplicate).status()).toBe(409);
  await expect(page.getByRole('alert')).toBeVisible();

  await page.getByLabel('Identificação do associado').fill(`${associate}-outro`);
  await page.getByRole('radio', { name: /^Não/ }).check();
  const anotherVote = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/pautas/${agenda.id}/votos`),
  );
  await page.getByRole('button', { name: 'Confirmar voto' }).click();
  expect((await anotherVote).status()).toBe(201);
  await expect(page.getByRole('meter', { name: 'Percentual de votos Sim' })).toHaveAttribute(
    'aria-valuenow',
    '50',
  );
  await expect(page.getByRole('meter', { name: 'Percentual de votos Não' })).toHaveAttribute(
    'aria-valuenow',
    '50',
  );

  // Uma nova leitura HTTP confirma que as duas opções foram persistidas no backend real.
  const resultResponse = await page.request.get(`/api/v1/pautas/${agenda.id}/resultado`);
  expect(resultResponse.status()).toBe(200);
  expect(await resultResponse.json()).toMatchObject({
    pautaId: agenda.id,
    total: 2,
    sim: 1,
    nao: 1,
    status: 'ABERTA',
  });
  await page.reload();
  await expect(page.getByRole('heading', { name: title, exact: true, level: 2 })).toBeVisible();
  await expect(page.getByRole('meter', { name: 'Percentual de votos Sim' })).toHaveAttribute(
    'aria-valuenow',
    '50',
  );
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1),
  ).toBe(true);
  expect(browserErrors).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath('assembleia.png'), fullPage: true });
});

test('busca vazia pode ser limpa e diálogo devolve o foco ao fechar', async ({ page }) => {
  await page.goto('/');
  const createButton = page.getByRole('button', { name: 'Nova pauta', exact: true });
  await createButton.click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(createButton).toBeFocused();
  await page.getByRole('searchbox', { name: 'Buscar pautas' }).fill(`inexistente-${Date.now()}`);
  await expect(page.getByRole('heading', { name: 'Nenhuma pauta encontrada' })).toBeVisible();
  await page.getByRole('button', { name: 'Limpar busca' }).click();
  await expect(page.getByRole('searchbox', { name: 'Buscar pautas' })).toHaveValue('');
  await expect(page.getByRole('heading', { name: 'Nenhuma pauta encontrada' })).not.toBeVisible();
});
