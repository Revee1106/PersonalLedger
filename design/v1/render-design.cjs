const { chromium } = require(process.argv[2] || 'playwright');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

(async () => {
  const output = path.join(__dirname, 'images');
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, ...(process.argv[3] ? { executablePath: process.argv[3] } : {}) });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1150 }, deviceScaleFactor: 1.5 });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const base = pathToFileURL(path.join(__dirname, 'index.html')).href;
  await page.goto(base + '?view=core');
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: path.join(output, '01-core-screens.png'), fullPage: true });
  await page.goto(base + '?view=access');
  await page.screenshot({ path: path.join(output, '02-login-and-reminder.png'), fullPage: true });

  const checks = [];
  await page.goto(base + '?view=phone');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: path.join(output, '03-home.png') });
  const phone = page.locator('#demo-phone');
  await phone.getByRole('button', { name: '记一笔', exact: true }).click();
  await phone.getByRole('button', { name: '清空', exact: true }).click();
  for (const key of ['1', '2', '.', '5', '0']) await phone.getByRole('button', { name: '输入 ' + key, exact: true }).click();
  const value = await phone.locator('.entered-amount').innerText();
  if (value !== '12.50') throw new Error('Amount entry failed: ' + value);
  await phone.getByRole('button', { name: '购物', exact: true }).click();
  await phone.getByRole('textbox', { name: '账目备注' }).fill('设计验证');
  await phone.getByRole('button', { name: '保 存', exact: true }).click();
  await phone.getByText('设计验证', { exact: true }).waitFor();
  const amount = await phone.locator('.transaction').first().locator('.transaction-amount').innerText();
  if (amount !== '−12.50') throw new Error('Saved amount mismatch');
  checks.push('金额键盘、分类选择、备注、保存及账本金额更新');
  await phone.getByRole('button', { name: '统计', exact: true }).click();
  if (await phone.locator('.money-main .whole').innerText() !== '3,299') throw new Error('Stats did not include saved entry');
  await phone.getByRole('button', { name: '收入', exact: true }).click();
  if (await phone.locator('.money-main .whole').innerText() !== '13,000') throw new Error('Income toggle failed');
  checks.push('保存后统计同步、支出和收入切换');

  await page.goto(base + '?view=phone');
  await page.locator('.demo-routes button[data-page="notification"]').evaluate(button => button.click());
  await page.screenshot({ path: path.join(output, '04-payment-reminder.png') });
  await phone.getByRole('button', { name: '修改', exact: true }).click();
  if (await phone.locator('.entered-amount').innerText() !== '28.00') throw new Error('Reminder edit amount missing');
  if (await phone.getByRole('textbox', { name: '账目备注' }).inputValue() !== '午餐') throw new Error('Reminder edit note missing');
  checks.push('支付提醒修改后预填金额、分类和备注');

  await page.goto(base + '?view=phone');
  await page.locator('.demo-routes button[data-page="login"]').evaluate(button => button.click());
  await phone.getByRole('textbox', { name: '手机号', exact: true }).fill('13800000000');
  await phone.getByRole('button', { name: '获取验证码', exact: true }).click();
  if (!(await phone.locator('.toast').innerText()).includes('123456')) throw new Error('SMS demo missing');
  await phone.getByRole('textbox', { name: '验证码', exact: true }).fill('123456');
  await phone.locator('input[name="consent"]').check();
  await phone.getByRole('button', { name: '手机号登录', exact: true }).click();
  if (await phone.getAttribute('data-page') !== 'home') throw new Error('Login demo failed');
  checks.push('手机号格式、模拟验证码、协议勾选和登录演示');

  await page.goto(base + '?view=phone');
  await page.locator('.demo-routes button[data-page="notification"]').evaluate(button => button.click());
  await phone.getByRole('button', { name: '确认记账', exact: true }).click();
  if (await phone.getAttribute('data-page') !== 'home') throw new Error('Notification confirmation failed');
  if (await phone.locator('.transaction').first().locator('.transaction-amount').innerText() !== '−28.00') throw new Error('Notification amount mismatch');
  checks.push('提醒确认后返回账本');

  await page.goto(base + '?view=phone');
  await phone.getByRole('button', { name: /笔消费待确认/ }).click();
  await phone.getByRole('button', { name: '忽略', exact: true }).first().click();
  if (await phone.locator('.pending-count').innerText() !== '1') throw new Error('Pending ignore failed');
  checks.push('待确认消费列表与忽略');

  const layouts = [];
  for (const size of [{width:390,height:844},{width:360,height:800},{width:412,height:915}]) {
    await page.setViewportSize(size);
    for (const screen of ['home','add','stats','login','notification']) {
      await page.locator('.demo-routes button[data-page="'+screen+'"]').evaluate(button => button.click());
      const layout = await phone.evaluate(el => {
        const viewport = el.querySelector('.viewport');
        const overflow = [...el.querySelectorAll('button,input,.transaction,.entry-amount,.category-grid,.keypad,.notification-card,.primary')].filter(node=>{
          const bounds=node.getBoundingClientRect();const box=el.getBoundingClientRect();
          return bounds.width && (bounds.left<box.left-1 || bounds.right>box.right+1 || (!node.closest('.page-content') && bounds.bottom>box.bottom+1));
        }).map(node=>node.className);
        return {page:el.dataset.page,horizontalOverflow:document.documentElement.scrollWidth>innerWidth,verticalOverflow:viewport.scrollHeight>viewport.clientHeight+1,overflow};
      });
      layouts.push({size,...layout});
      if (layout.horizontalOverflow || layout.verticalOverflow || layout.overflow.length) throw new Error('Layout overflow: '+JSON.stringify({size,...layout}));
    }
  }
  if (errors.length) throw new Error('Browser errors: '+errors.join('; '));
  fs.writeFileSync(path.join(__dirname,'verification.json'),JSON.stringify({checks,layouts,browserErrors:errors},null,2));
  console.log(JSON.stringify({screenshots:fs.readdirSync(output),checks,layoutChecks:layouts.length,browserErrors:errors}));
  await browser.close();
})().catch(error=>{console.error(error);process.exit(1)});
