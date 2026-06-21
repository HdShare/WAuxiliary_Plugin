from DrissionPage import ChromiumPage
import requests
import json
import time
import os

DATA_FILE = 'data.json'
API_URL = 'https://aifwzs.gdmec.edu.cn/api/chat-messages'

def load_cookies():
    """从 data.json 读取 cookies"""
    if not os.path.exists(DATA_FILE):
        return None
    with open(DATA_FILE, 'r') as f:
        data = json.load(f)
    if time.time() - data.get('save_time', 0) > 20 * 3600:
        print('⚠️ cookies 已过期，重新获取...')
        return None
    print('✅ 从 data.json 读取 cookies')
    return data['cookies']

def save_cookies(cookies):
    with open(DATA_FILE, 'w') as f:
        json.dump({'cookies': cookies, 'save_time': time.time()}, f, indent=2)
    print('✅ cookies 已保存到 data.json')

def get_cookies_from_browser():
    """浏览器获取 cookies，已登录就直接拿，没登录才执行登录流程"""
    print('🌐 打开浏览器...')
    page = ChromiumPage()
    page.get('https://aifwzs.gdmec.edu.cn')
    time.sleep(3)

    current_url = page.url

    # 判断是否已登录（没跳到登录页说明已登录）
    if 'auth.gdmec.edu.cn' not in current_url and 'login' not in current_url.lower():
        print('✅ 浏览器已登录，直接拿 cookies')
        time.sleep(2)
        cookies_list = page.cookies()
        cookies = {c['name']: c['value'] for c in cookies_list}
        page.quit()
        return cookies

    # 没登录，执行登录流程
    print('🔐 未登录，执行登录...')
    page.wait.url_change('auth.gdmec.edu.cn', timeout=10)
    page.ele('text=账号登录').click()
    page.ele('tag:input@@name=username').input('25120327')
    page.ele('tag:input@@name=password').input('Aa147258369')
    page.ele('xpath://*[@id="login-submit"]').click()
    time.sleep(2)
    page.ele('xpath:/html/body/div[3]/div/div[2]/div/div[1]/div/div[2]/div/div[2]/button/span', timeout=5).click()
    page.wait.url_change('aifwzs.gdmec.edu.cn', timeout=15)
    time.sleep(5)

    cookies_list = page.cookies()
    cookies = {c['name']: c['value'] for c in cookies_list}
    page.quit()
    return cookies

def ask(question, conversation_id=None):
    cookies = load_cookies()
    if not cookies:
        cookies = get_cookies_from_browser()
        save_cookies(cookies)

    session = requests.Session()
    session.cookies.update(cookies)

    payload = {'response_mode': 'streaming', 'files': [], 'query': question, 'inputs': {}}
    if conversation_id:
        payload['conversation_id'] = conversation_id

    resp = session.post(API_URL, json=payload,
        headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'},
        stream=True, timeout=60)

    answer = ''
    for line in resp.iter_lines():
        line = line.decode('utf-8')
        if not line or not line.startswith('data: '):
            continue
        data = json.loads(line[6:])
        if data.get('event') == 'message':
            answer += data.get('answer', '')
        if data.get('event') == 'workflow_finished':
            break

    # 失败则重新登录
    if not answer:
        print('⚠️ token 可能失效，重新登录...')
        cookies = get_cookies_from_browser()
        save_cookies(cookies)
        session.cookies.update(cookies)
        resp = session.post(API_URL, json=payload,
            headers={'User-Agent': 'Mozilla/5.0'}, stream=True, timeout=60)
        for line in resp.iter_lines():
            line = line.decode('utf-8')
            if not line or not line.startswith('data: '):
                continue
            data = json.loads(line[6:])
            if data.get('event') == 'message':
                answer += data.get('answer', '')
            if data.get('event') == 'workflow_finished':
                break

    return answer

if __name__ == '__main__':
    answer = ask('你是什么程序')
    print(f'\n回答：{answer}')