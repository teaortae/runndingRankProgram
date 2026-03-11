#!/usr/bin/env python3
import json
import sys
import time
import warnings

warnings.filterwarnings(
    "ignore",
    message=r"urllib3 v2 only supports OpenSSL 1\.1\.1\+.*",
)


def emit(payload, code=0):
    print(json.dumps(payload, ensure_ascii=False))
    raise SystemExit(code)


try:
    import browser_cookie3
    from selenium import webdriver
    from selenium.webdriver.chrome.options import Options
    from selenium.webdriver.common.by import By
    from selenium.webdriver.common.keys import Keys
    from selenium.webdriver.support.ui import WebDriverWait
    from selenium.webdriver.support import expected_conditions as EC
except Exception as exc:
    emit(
        {
            "status": "error",
            "message": "필수 Python 패키지가 없습니다. `python3 -m pip install --user selenium browser-cookie3`를 먼저 실행하세요.",
            "detail": str(exc),
        },
        1,
    )


def add_google_cookies(driver):
    driver.get("https://docs.google.com/")
    for cookie in browser_cookie3.chrome():
        if not (
            cookie.domain.endswith("google.com")
            or cookie.domain.endswith("docs.google.com")
        ):
            continue
        payload = {
            "name": cookie.name,
            "value": cookie.value,
            "path": cookie.path or "/",
            "secure": bool(cookie.secure),
        }
        domain = cookie.domain.lstrip(".")
        if domain:
            payload["domain"] = domain
        if cookie.expires:
            try:
                payload["expiry"] = int(cookie.expires)
            except Exception:
                pass
        try:
            driver.add_cookie(payload)
        except Exception:
            pass


def main():
    if len(sys.argv) < 2:
        emit({"status": "error", "message": "usage: sync_sheet.py <sheet-url>"}, 1)

    sheet_url = sys.argv[1].strip()
    tsv = sys.stdin.read()
    if not sheet_url:
        emit({"status": "error", "message": "시트 링크가 비어 있습니다."}, 1)
    if not tsv.strip():
        emit({"status": "error", "message": "시트에 쓸 내용이 없습니다."}, 1)

    options = Options()
    options.binary_location = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
    options.add_argument("--headless=new")
    options.add_argument("--window-size=1600,1200")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")

    driver = webdriver.Chrome(options=options)
    try:
        add_google_cookies(driver)
        driver.get(sheet_url)
        WebDriverWait(driver, 40).until(lambda d: "Google Sheets" in d.title)
        WebDriverWait(driver, 40).until(
            EC.presence_of_element_located((By.ID, "t-name-box"))
        )
        WebDriverWait(driver, 40).until(
            lambda d: d.execute_script(
                'return !!document.querySelector("textarea.trix-offscreen")'
            )
        )

        name_box = driver.find_element(By.ID, "t-name-box")
        name_box.click()
        name_box.clear()
        name_box.send_keys("A1")
        name_box.send_keys(Keys.ENTER)
        time.sleep(0.7)

        driver.execute_script(
            """
            const text = arguments[0];
            const textarea = document.querySelector('textarea.trix-offscreen');
            const data = new DataTransfer();
            data.setData('text/plain', text);
            textarea.focus();
            const event = new ClipboardEvent('paste', {
                clipboardData: data,
                bubbles: true,
                cancelable: true,
            });
            textarea.dispatchEvent(event);
            """,
            tsv,
        )

        time.sleep(3)
        emit(
            {
                "status": "ok",
                "message": "Google Sheets에 반영했습니다.",
                "sheet_url": driver.current_url,
            },
            0,
        )
    except Exception as exc:
        emit({"status": "error", "message": f"시트 동기화 실패: {exc}"}, 1)
    finally:
        driver.quit()


if __name__ == "__main__":
    main()
