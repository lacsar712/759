// 通用工具函数

// HTML转义函数，防止XSS攻击
function escapeHtml(text) {
    if (text == null) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// API 请求封装
async function request(url, options = {}) {
    const defaultOptions = {
        headers: {
            'Content-Type': 'application/json'
        },
        credentials: 'include' // 携带 Cookie
    };

    const finalOptions = { ...defaultOptions, ...options };

    try {
        const response = await fetch(url, finalOptions);
        const data = await response.json();

        if (data.code === 0) {
            return data.data;
        } else {
            throw new Error(data.message || '请求失败');
        }
    } catch (error) {
        throw error;
    }
}

// GET 请求
async function get(url) {
    return request(url, { method: 'GET' });
}

// POST 请求
async function post(url, body) {
    return request(url, {
        method: 'POST',
        body: JSON.stringify(body)
    });
}

// PUT 请求
async function put(url, body) {
    return request(url, {
        method: 'PUT',
        body: JSON.stringify(body)
    });
}

// DELETE 请求
async function del(url) {
    return request(url, { method: 'DELETE' });
}

// 显示提示框
function showModal(title, message, type = 'info') {
    const modal = document.createElement('div');
    modal.className = 'modal show';
    modal.innerHTML = `
        <div class="modal-content">
            <div class="modal-header">
                <h3 class="modal-title"></h3>
                <span class="modal-close" onclick="this.closest('.modal').remove()">&times;</span>
            </div>
            <div class="alert alert-${type}"></div>
            <button class="btn btn-primary btn-block" onclick="this.closest('.modal').remove()">确定</button>
        </div>
    `;

    // 使用textContent防止XSS
    modal.querySelector('.modal-title').textContent = title;
    modal.querySelector('.alert').textContent = message;

    document.body.appendChild(modal);
}

// 显示成功提示
function showSuccess(message) {
    showModal('成功', message, 'success');
}

// 显示错误提示
function showError(message) {
    showModal('错误', message, 'error');
}

// 显示信息提示
function showInfo(message) {
    showModal('提示', message, 'info');
}

// 显示确认对话框
function showConfirm(title, message, onConfirm) {
    const modal = document.createElement('div');
    modal.className = 'modal show';
    modal.innerHTML = `
        <div class="modal-content">
            <div class="modal-header">
                <h3 class="modal-title"></h3>
                <span class="modal-close" onclick="this.closest('.modal').remove()">&times;</span>
            </div>
            <div class="alert alert-info"></div>
            <div style="display: flex; gap: 10px;">
                <button class="btn btn-secondary" style="flex: 1;" onclick="this.closest('.modal').remove()">取消</button>
                <button class="btn btn-primary" style="flex: 1;" id="confirmBtn">确定</button>
            </div>
        </div>
    `;

    // 使用textContent防止XSS
    modal.querySelector('.modal-title').textContent = title;
    modal.querySelector('.alert').textContent = message;

    document.body.appendChild(modal);

    document.getElementById('confirmBtn').onclick = () => {
        modal.remove();
        onConfirm();
    };
}

// 表单验证
function validateForm(formId) {
    const form = document.getElementById(formId);
    const inputs = form.querySelectorAll('input[required], select[required], textarea[required]');
    let isValid = true;

    inputs.forEach(input => {
        const errorDiv = input.nextElementSibling;
        if (errorDiv && errorDiv.classList.contains('error-message')) {
            errorDiv.remove();
        }
        input.classList.remove('error-input');

        if (!input.value.trim()) {
            isValid = false;
            input.classList.add('error-input');
            const error = document.createElement('div');
            error.className = 'error-message';
            error.textContent = input.getAttribute('data-error') || '此字段不能为空';
            input.parentNode.insertBefore(error, input.nextSibling);
        }
    });

    return isValid;
}

// 格式化时间
function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '';

    // 如果已经是正确格式的字符串（yyyy-MM-dd HH:mm:ss），直接返回
    if (typeof dateTimeStr === 'string' && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(dateTimeStr)) {
        return dateTimeStr;
    }

    // 如果是ISO格式（带T），需要转换
    const date = new Date(dateTimeStr);
    if (isNaN(date.getTime())) {
        return dateTimeStr; // 无法解析，返回原值
    }

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

// 格式化日期
function formatDate(dateStr) {
    if (!dateStr) return '';
    return dateStr.split(' ')[0];
}

// 获取 URL 参数
function getUrlParam(name) {
    const urlParams = new URLSearchParams(window.location.search);
    return urlParams.get(name);
}

// 跳转页面
function navigateTo(url) {
    window.location.href = url;
}

// 登出
async function logout() {
    try {
        await post('/api/auth/logout');
        navigateTo('/student/login.html');
    } catch (error) {
        showError(error.message);
    }
}
