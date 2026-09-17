import asyncio
import importlib.util
import os
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import patch


class _FakeRouter:
    def __init__(self):
        self.handlers = []

    def add_event_handler(self, _event, _handler):
        self.handlers.append((_event, _handler))


class _FakeApp:
    def __init__(self):
        self.router = _FakeRouter()
        self.routes = [object()]
        github_canary = "ghp_" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8"
        openai_canary = "sk-proj-" + "Z9y8X7w6V5u4T3s2R1q0" * 3
        self.upstream_routes = [
            _FakeRoute(
                "/scan",
                _FakeScanResponse(
                    [
                        {
                            "description": f"YARA match: {github_canary}",
                            "metadata": {"evidence": openai_canary},
                        }
                    ]
                ),
            ),
            _FakeRoute(
                "/scan-upload",
                _FakeScanResponse([{"description": "No credentials", "metadata": {"safe": True}}]),
            ),
        ]

    def middleware(self, _kind):
        return lambda function: function


class _FakeResponse:
    def __init__(self, status_code, content, headers):
        self.status_code = status_code
        self.content = content
        self.headers = headers


class _FakeScanResponse:
    def __init__(self, findings):
        self.findings = findings
        self.status_code = 200
        self.headers = {"X-Contract": "preserved"}


class _FakeRoute:
    def __init__(self, path, response):
        self.path = path
        self.methods = {"POST"}

        async def endpoint():
            return response

        self.endpoint = endpoint
        self.dependant = types.SimpleNamespace(call=endpoint)


class _Request:
    method = "POST"
    url = types.SimpleNamespace(path="/scan-upload")


def _load_module(environment=None):
    fastapi = types.ModuleType("fastapi")
    fastapi.Request = object
    responses = types.ModuleType("fastapi.responses")
    responses.JSONResponse = _FakeResponse
    api = types.ModuleType("skill_scanner.api.api")
    api.app = _FakeApp()
    router = types.ModuleType("skill_scanner.api.router")
    router.MAX_UPLOAD_SIZE_BYTES = -1
    router._API_UPLOAD_ROOT = Path(tempfile.gettempdir()) / "skill_scanner_current"
    router.router = types.SimpleNamespace(routes=api.app.upstream_routes)
    stubs = {
        "fastapi": fastapi,
        "fastapi.responses": responses,
        "skill_scanner": types.ModuleType("skill_scanner"),
        "skill_scanner.api": types.ModuleType("skill_scanner.api"),
        "skill_scanner.api.api": api,
        "skill_scanner.api.router": router,
    }
    with patch.dict(os.environ, environment or {}, clear=True), patch.dict(sys.modules, stubs):
        module_path = Path(__file__).parents[1] / "skillhub_scanner_app.py"
        spec = importlib.util.spec_from_file_location("skillhub_scanner_app_under_test", module_path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module._router_stub = router
        return module


class SkillHubScannerAppTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.module = _load_module()

    async def test_excess_scan_is_rejected(self):
        self.module._active_scans = 1

        response = await self.module.limit_concurrent_scans(_Request(), lambda _request: None)

        self.assertEqual(503, response.status_code)
        self.assertEqual("30", response.headers["Retry-After"])

    async def test_client_disconnect_keeps_slot_until_scan_finishes(self):
        release = asyncio.Event()

        async def scan(_request):
            await release.wait()
            return "done"

        request_task = asyncio.create_task(self.module.limit_concurrent_scans(_Request(), scan))
        await asyncio.sleep(0)
        request_task.cancel()
        await asyncio.sleep(0)

        self.assertEqual(1, self.module._active_scans)
        response = await self.module.limit_concurrent_scans(_Request(), scan)
        self.assertEqual(503, response.status_code)

        release.set()
        with self.assertRaises(asyncio.CancelledError):
            await request_task
        self.assertEqual(0, self.module._active_scans)

    async def test_hard_timeout_requests_process_restart(self):
        self.module._HARD_TIMEOUT_SECONDS = 0.01

        async def stuck_scan(_request):
            await asyncio.Event().wait()

        with patch.object(
                self.module,
                "_restart_after_hard_timeout",
                side_effect=RuntimeError("restart requested")) as restart:
            with self.assertRaisesRegex(RuntimeError, "restart requested"):
                await self.module.limit_concurrent_scans(_Request(), stuck_scan)

        restart.assert_called_once_with("/scan-upload")
        self.assertEqual(0, self.module._active_scans)

    async def test_startup_cleanup_removes_only_scanner_directories(self):
        with tempfile.TemporaryDirectory() as temp_root:
            root = Path(temp_root)
            stale = root / "skill_scanner_abcd"
            unrelated = root / "skillhub-data"
            stale.mkdir()
            unrelated.mkdir()

            self.module._cleanup_stale_scan_directories(root)

            self.assertFalse(stale.exists())
            self.assertTrue(unrelated.exists())

    async def test_startup_cleanup_keeps_current_upstream_upload_directory(self):
        with tempfile.TemporaryDirectory() as temp_root:
            root = Path(temp_root)
            current = root / "skill_scanner_current"
            stale = root / "skill_scanner_stale"
            current.mkdir()
            stale.mkdir()
            self.module._router_stub._API_UPLOAD_ROOT = current

            self.module._cleanup_stale_scan_directories(root)

            self.assertTrue(current.exists())
            self.assertFalse(stale.exists())

    async def test_default_upload_limit_matches_skillhub_package_limit(self):
        self.assertEqual(110100480, self.module._router_stub.MAX_UPLOAD_SIZE_BYTES)

    async def test_upload_limit_can_be_overridden_by_environment(self):
        module = _load_module({"SKILLHUB_SCANNER_MAX_UPLOAD_SIZE_BYTES": "123456"})

        self.assertEqual(123456, module._router_stub.MAX_UPLOAD_SIZE_BYTES)

    async def test_upload_limit_is_at_least_one_byte(self):
        module = _load_module({"SKILLHUB_SCANNER_MAX_UPLOAD_SIZE_BYTES": "0"})

        self.assertEqual(1, module._router_stub.MAX_UPLOAD_SIZE_BYTES)

    async def test_scan_findings_redact_supported_tokens_without_changing_response_contract(self):
        route = next(route for route in self.module._router_stub.router.routes if route.path == "/scan")

        response = await route.endpoint()

        self.assertNotIn("ghp_", str(response.findings))
        self.assertNotIn("sk-proj-", str(response.findings))
        self.assertEqual(200, response.status_code)
        self.assertEqual({"X-Contract": "preserved"}, response.headers)

    async def test_safe_scan_findings_are_unchanged(self):
        route = next(route for route in self.module._router_stub.router.routes if route.path == "/scan-upload")
        expected = [{"description": "No credentials", "metadata": {"safe": True}}]

        response = await route.dependant.call()

        self.assertEqual(expected, response.findings)

    async def test_startup_cleanup_is_registered_on_the_upstream_router(self):
        self.assertEqual(
            [("startup", self.module._cleanup_stale_scan_directories)],
            self.module.app.router.handlers,
        )


if __name__ == "__main__":
    unittest.main()
