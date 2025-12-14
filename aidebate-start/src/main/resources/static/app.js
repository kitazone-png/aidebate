// ========== Global State ==========
const appState = {
    sessionId: null,
    sessionStatus: 'NOT_STARTED',
    language: 'en',
    currentRound: 1,
    maxRounds: 5,
    timeRemaining: 180,
    timerInterval: null,
    chatMessages: [], // Array of {id, speaker, role, content, timestamp, round, messageType}
    streamingState: {
        active: false,
        speaker: null,
        content: '',
        eventType: ''
    },
    scores: { userTotal: 0, aiTotal: 0 },
    selectedTopic: null,
    userSide: null,
    translations: {},
    streamingSSE: null
};

// ========== I18n Translation System ==========
let i18nEn = {};
let i18nZh = {};

async function loadTranslations() {
    try {
        const [enResponse, zhResponse] = await Promise.all([
            fetch('/i18n.en.json'),
            fetch('/i18n.zh.json')
        ]);
        i18nEn = await enResponse.json();
        i18nZh = await zhResponse.json();
        
        // Detect browser language
        const browserLang = navigator.language.toLowerCase();
        if (browserLang.startsWith('zh')) {
            appState.language = 'zh';
        }
        
        // Load from localStorage if available
        const savedLang = localStorage.getItem('debateLang');
        if (savedLang) {
            appState.language = savedLang;
        }
        
        applyTranslations();
    } catch (error) {
        console.error('Failed to load translations:', error);
    }
}

function t(key) {
    const keys = key.split('.');
    let value = appState.language === 'zh' ? i18nZh : i18nEn;
    
    for (const k of keys) {
        value = value[k];
        if (!value) return key;
    }
    
    return value;
}

function applyTranslations() {
    // Header
    document.getElementById('header-title').textContent = '⚔️ ' + t('header.title') + ' ⚔️';
    document.getElementById('header-subtitle').textContent = t('header.subtitle');
    document.getElementById('lang-label').textContent = appState.language === 'zh' ? '中文' : 'EN';
    
    // Topic selector
    document.getElementById('topic-label').textContent = t('topics.currentTopic') + ':';
    document.getElementById('btn-select-topic').textContent = t('buttons.selectTopic');
    
    // Flow panel
    document.getElementById('flow-title').textContent = t('flow.title');
    document.getElementById('label-current-round').textContent = t('flow.currentRound') + ':';
    document.getElementById('label-time').textContent = t('flow.timeRemaining') + ':';
    
    document.getElementById('flow-r1-label').textContent = t('flow.rounds.round1');
    document.getElementById('flow-r2-label').textContent = t('flow.rounds.round2');
    document.getElementById('flow-r3-label').textContent = t('flow.rounds.round3');
    document.getElementById('flow-r4-label').textContent = t('flow.rounds.round4');
    document.getElementById('flow-r5-label').textContent = t('flow.rounds.round5');
    document.getElementById('flow-j-label').textContent = t('flow.rounds.judging');
    
    document.getElementById('rules-title').textContent = t('flow.rules');
    document.getElementById('rule-max-length').textContent = '• ' + t('flow.maxLength');
    document.getElementById('rule-time-limit').textContent = '• ' + t('flow.timeLimit');
    document.getElementById('rule-scoring').textContent = '• ' + t('flow.scoringCriteria');
    document.getElementById('rule-prohibited').textContent = '• ' + t('flow.prohibited');
    
    // Score board
    document.getElementById('score-title').textContent = t('score.title');
    document.getElementById('score-user-label').textContent = t('debate.user');
    document.getElementById('score-ai-label').textContent = t('debate.ai');
    
    // Chat area
    document.getElementById('chat-empty-text').textContent = t('chat.emptyState');
    document.getElementById('argument-input').placeholder = t('input.placeholder');
    document.getElementById('btn-preview').textContent = t('buttons.preview');
    document.getElementById('btn-submit').textContent = t('input.submit');
    
    // Modal
    document.getElementById('modal-topics-title').textContent = t('topics.title');
    
    // Footer
    document.getElementById('footer-text').textContent = t('footer.text');
    
    // Buttons
    document.getElementById('btn-new-debate').textContent = t('buttons.startNewDebate');
    
    updateCharCounter();
}

function toggleLanguage() {
    appState.language = appState.language === 'en' ? 'zh' : 'en';
    localStorage.setItem('debateLang', appState.language);
    applyTranslations();
}

// ========== Topic Management ==========
async function loadTopics() {
    try {
        const response = await fetch('/api/topics');
        const topics = await response.json();
        
        const topicsList = document.getElementById('topics-list');
        if (topics.length === 0) {
            topicsList.innerHTML = `<div class="col-span-3 text-center text-gray-500">${t('topics.noTopics')}</div>`;
            return;
        }
        
        topicsList.innerHTML = topics.map(topic => `
            <div class="p-4 bg-wasteland-darker rounded border border-wasteland-gold hover:border-wasteland-gold-light transition cursor-pointer" 
                 onclick="selectTopic(${topic.topicId}, '${escapeHtml(topic.title)}')">
                <h3 class="font-bold text-wasteland-gold-light mb-2 text-sm">${escapeHtml(topic.title)}</h3>
                <p class="text-xs text-gray-400 mb-2">${escapeHtml(topic.description || 'No description')}</p>
                <div class="flex justify-between items-center text-xs text-gray-500">
                    <span class="bg-wasteland-blue px-2 py-1 rounded">${escapeHtml(topic.category || 'General')}</span>
                </div>
            </div>
        `).join('');
    } catch (error) {
        console.error('Error loading topics:', error);
        document.getElementById('topics-list').innerHTML = 
            `<div class="col-span-3 text-center text-red-400">${t('messages.errorLoadingTopics')}</div>`;
    }
}

function selectTopic(topicId, topicTitle) {
    appState.selectedTopic = { id: topicId, title: topicTitle };
    
    const sidePrompt = confirm(
        t('messages.selectTopicPrompt').replace('{topic}', topicTitle)
    );
    
    appState.userSide = sidePrompt ? 'AFFIRMATIVE' : 'NEGATIVE';
    
    // Update UI
    document.getElementById('selected-topic-display').textContent = topicTitle;
    document.getElementById('btn-select-topic').textContent = t('buttons.changeTopic');
    
    // Close modal
    closeTopicModal();
    
    // Initialize session
    initializeSession();
}

// ========== Session Management ==========
async function initializeSession() {
    try {
        const response = await fetch('/api/debates/init', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                topicId: appState.selectedTopic.id,
                userId: 1,
                userSide: appState.userSide,
                aiConfig: {
                    personality: 'Analytical',
                    expertiseLevel: 'Expert'
                }
            })
        });
        
        const data = await response.json();
        appState.sessionId = data.sessionId;
        
        // Start the session
        await fetch(`/api/debates/${data.sessionId}/start`, { method: 'POST' });
        
        // Reset state
        appState.sessionStatus = 'IN_PROGRESS';
        appState.chatMessages = [];
        appState.currentRound = 1;
        appState.scores = { userTotal: 0, aiTotal: 0 };
        
        updateSessionStatusUI();
        updateUI();
        
        // Start timer
        startTimer();
        
        // Show welcome message
        addChatMessage({
            id: 'welcome-' + Date.now(),
            speaker: 'MODERATOR',
            role: 'MODERATOR',
            content: t('moderator.welcome')
                .replace('{topic}', appState.selectedTopic.title)
                .replace('{side}', t('sides.' + appState.userSide))
                .replace('{round}', '1'),
            timestamp: new Date().toISOString(),
            round: 1,
            messageType: 'ANNOUNCEMENT'
        });
        
        showToast(t('messages.debateStarted').replace('{side}', t('sides.' + appState.userSide)), 'success');
    } catch (error) {
        console.error('Error initializing session:', error);
        showToast(t('messages.failedToStart'), 'error');
    }
}

async function submitArgument() {
    const argumentText = document.getElementById('argument-input').value.trim();
    
    if (!argumentText) {
        showToast(t('messages.pleaseEnterArgument'), 'warning');
        return;
    }
    
    if (!appState.sessionId) {
        showToast(t('messages.pleaseSelectTopic'), 'warning');
        return;
    }
    
    if (appState.sessionStatus !== 'IN_PROGRESS') {
        showToast(t('messages.debateEndedCannotSubmit'), 'error');
        return;
    }
    
    try {
        // Disable submit button
        document.getElementById('btn-submit').disabled = true;
        document.getElementById('argument-input').disabled = true;
        
        // Clear input immediately
        document.getElementById('argument-input').value = '';
        updateCharCounter();
        
        // Connect to streaming endpoint
        connectStreamingSSE(argumentText, appState.currentRound);
        
    } catch (error) {
        console.error('Error submitting argument:', error);
        showToast(t('messages.failedToSubmit').replace('{error}', error.message), 'error');
        document.getElementById('btn-submit').disabled = false;
        document.getElementById('argument-input').disabled = false;
    }
}

// ========== SSE Streaming ==========
function connectStreamingSSE(argumentText, roundNumber) {
    if (appState.streamingSSE) {
        appState.streamingSSE.close();
    }
    
    const url = `/api/debates/${appState.sessionId}/submit-argument-stream`;
    
    fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            argumentText: argumentText,
            roundNumber: roundNumber,
            language: appState.language
        })
    }).then(response => {
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        
        function processStream() {
            reader.read().then(({ done, value }) => {
                if (done) {
                    closeStreamingSSE();
                    return;
                }
                
                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop(); // Keep incomplete line in buffer
                
                for (const line of lines) {
                    if (line.startsWith('event:')) {
                        const eventType = line.substring(6).trim();
                        handleSSEEvent(eventType);
                    } else if (line.startsWith('data:')) {
                        const data = line.substring(5).trim();
                        if (data) {
                            try {
                                const eventData = JSON.parse(data);
                                handleSSEData(eventData);
                            } catch (e) {
                                console.error('Error parsing SSE data:', e);
                            }
                        }
                    }
                }
                
                processStream();
            }).catch(error => {
                console.error('SSE stream error:', error);
                closeStreamingSSE();
            });
        }
        
        processStream();
    }).catch(error => {
        console.error('Error connecting to streaming endpoint:', error);
        showToast(t('messages.failedToSubmit').replace('{error}', error.message), 'error');
        document.getElementById('btn-submit').disabled = false;
        document.getElementById('argument-input').disabled = false;
    });
}

let currentEventType = '';

function handleSSEEvent(eventType) {
    currentEventType = eventType;
}

function handleSSEData(data) {
    console.log('SSE Event:', currentEventType, data);
    
    switch (currentEventType) {
        case 'user_argument':
            handleUserArgumentEvent(data);
            break;
        case 'moderator_summary':
            handleModeratorEvent(data, 'SUMMARY');
            break;
        case 'moderator_evaluation':
            handleModeratorEvent(data, 'EVALUATION');
            break;
        case 'moderator_announcement':
            handleModeratorEvent(data, 'ANNOUNCEMENT');
            break;
        case 'ai_argument':
            handleAIArgumentEvent(data);
            break;
        case 'scores_update':
            handleScoresUpdate(data);
            break;
        case 'stream_complete':
            handleStreamComplete(data);
            break;
        case 'error':
            handleStreamError(data);
            break;
    }
}

function handleUserArgumentEvent(data) {
    if (data.complete) {
        addChatMessage({
            id: 'user-' + data.argumentId,
            speaker: appState.userSide,
            role: data.role,
            content: data.content,
            timestamp: data.timestamp,
            round: data.round,
            messageType: 'ARGUMENT'
        });
    }
}

function handleModeratorEvent(data, messageType) {
    if (data.complete) {
        // Hide streaming preview and add to chat
        hideStreamingPreview();
        addChatMessage({
            id: 'moderator-' + Date.now(),
            speaker: 'MODERATOR',
            role: 'MODERATOR',
            content: data.chunk,
            timestamp: data.timestamp,
            round: appState.currentRound,
            messageType: messageType
        });
    } else {
        // Show in streaming preview
        showStreamingPreview('MODERATOR', data.chunk);
    }
}

function handleAIArgumentEvent(data) {
    const opponentSide = appState.userSide === 'AFFIRMATIVE' ? 'NEGATIVE' : 'AFFIRMATIVE';
    
    if (data.complete) {
        // Hide streaming preview and add to chat
        hideStreamingPreview();
        addChatMessage({
            id: 'ai-' + (data.argumentId || Date.now()),
            speaker: opponentSide,
            role: data.role,
            content: data.chunk,
            timestamp: data.timestamp,
            round: data.round,
            messageType: 'ARGUMENT'
        });
    } else {
        // Show in streaming preview
        showStreamingPreview(opponentSide, data.chunk);
    }
}

function handleScoresUpdate(data) {
    appState.scores.userTotal = data.userTotal;
    appState.scores.aiTotal = data.aiTotal;
    updateScores();
}

function handleStreamComplete(data) {
    console.log('Stream complete:', data);
    closeStreamingSSE();
    
    // Increment round
    appState.currentRound++;
    
    if (appState.currentRound > appState.maxRounds) {
        completeDebate();
    } else {
        updateUI();
        // Re-enable input
        document.getElementById('btn-submit').disabled = false;
        document.getElementById('argument-input').disabled = false;
        document.getElementById('argument-input').focus();
    }
}

function handleStreamError(data) {
    console.error('Stream error:', data);
    showToast(data.error || 'An error occurred during streaming', 'error');
    closeStreamingSSE();
    document.getElementById('btn-submit').disabled = false;
    document.getElementById('argument-input').disabled = false;
}

function closeStreamingSSE() {
    if (appState.streamingSSE) {
        appState.streamingSSE.close();
        appState.streamingSSE = null;
    }
    hideStreamingPreview();
}

// ========== Chat UI Functions ==========
function addChatMessage(message) {
    appState.chatMessages.push(message);
    
    const chatContainer = document.getElementById('chat-messages');
    const emptyState = document.getElementById('chat-empty-state');
    
    // Hide empty state
    if (emptyState) {
        emptyState.style.display = 'none';
    }
    
    // Create message element
    const messageEl = createMessageElement(message);
    chatContainer.appendChild(messageEl);
    
    // Scroll to bottom
    scrollToBottom();
}

function createMessageElement(message) {
    const div = document.createElement('div');
    
    // Determine alignment
    const isLeft = message.speaker === 'AFFIRMATIVE' || message.speaker === 'MODERATOR';
    div.className = `chat-message ${isLeft ? 'chat-message-left' : 'chat-message-right'}`;
    
    // Create message bubble
    const bubble = document.createElement('div');
    bubble.className = 'message-bubble';
    
    // Add speaker-specific styling
    if (message.speaker === 'MODERATOR') {
        bubble.classList.add('message-bubble-moderator');
    } else if (message.speaker === 'AFFIRMATIVE') {
        bubble.classList.add('message-bubble-affirmative');
    } else if (message.speaker === 'NEGATIVE') {
        bubble.classList.add('message-bubble-negative');
    }
    
    // Create header
    const header = document.createElement('div');
    header.className = 'flex items-center gap-2 mb-1';
    
    const indicator = document.createElement('div');
    indicator.className = 'w-2 h-2 rounded-full';
    if (message.speaker === 'MODERATOR') {
        indicator.classList.add('bg-amber-500');
    } else if (message.speaker === 'AFFIRMATIVE') {
        indicator.classList.add('bg-blue-500');
    } else {
        indicator.classList.add('bg-red-500');
    }
    
    const speakerLabel = document.createElement('span');
    speakerLabel.className = 'text-xs font-bold';
    speakerLabel.textContent = getSpeakerLabel(message.speaker);
    
    const timestamp = document.createElement('span');
    timestamp.className = 'text-xs text-gray-500';
    timestamp.textContent = formatTimestamp(message.timestamp);
    
    header.appendChild(indicator);
    header.appendChild(speakerLabel);
    header.appendChild(timestamp);
    
    // Create content
    const content = document.createElement('div');
    content.className = 'text-sm';
    content.textContent = message.content;
    
    // Create footer (round info)
    const footer = document.createElement('div');
    footer.className = 'text-xs text-gray-500 mt-1';
    footer.textContent = `${t('debate.round')} ${message.round}`;
    
    bubble.appendChild(header);
    bubble.appendChild(content);
    bubble.appendChild(footer);
    
    div.appendChild(bubble);
    
    return div;
}

function getSpeakerLabel(speaker) {
    if (speaker === 'MODERATOR') {
        return t('chat.moderator');
    } else if (speaker === 'AFFIRMATIVE') {
        return t('chat.affirmative');
    } else if (speaker === 'NEGATIVE') {
        return t('chat.negative');
    }
    return speaker;
}

function formatTimestamp(timestamp) {
    const date = new Date(timestamp);
    return date.toLocaleTimeString(appState.language === 'zh' ? 'zh-CN' : 'en-US', {
        hour: '2-digit',
        minute: '2-digit'
    });
}

function showStreamingPreview(speaker, content) {
    const container = document.getElementById('streaming-preview-container');
    const speakerEl = document.getElementById('streaming-speaker');
    const contentEl = document.getElementById('streaming-content');
    
    container.classList.remove('hidden');
    speakerEl.textContent = getSpeakerLabel(speaker) + ' ' + t('chat.streaming');
    contentEl.textContent = content;
    
    appState.streamingState.active = true;
    appState.streamingState.speaker = speaker;
    appState.streamingState.content = content;
    
    scrollToBottom();
}

function hideStreamingPreview() {
    const container = document.getElementById('streaming-preview-container');
    container.classList.add('hidden');
    
    appState.streamingState.active = false;
    appState.streamingState.speaker = null;
    appState.streamingState.content = '';
}

function scrollToBottom() {
    const chatContainer = document.getElementById('chat-messages');
    setTimeout(() => {
        chatContainer.scrollTop = chatContainer.scrollHeight;
    }, 100);
}

// ========== Preview Function ==========
async function previewArgument() {
    if (!appState.sessionId) {
        showToast(t('messages.pleaseSelectTopic'), 'warning');
        return;
    }
    
    try {
        const response = await fetch(`/api/debates/${appState.sessionId}/simulate-argument`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                roundNumber: appState.currentRound
            })
        });
        
        const data = await response.json();
        document.getElementById('argument-input').value = data.simulatedArgument;
        updateCharCounter();
    } catch (error) {
        console.error('Error generating preview:', error);
    }
}

// ========== Complete Debate ==========
async function completeDebate() {
    try {
        const response = await fetch(`/api/debates/${appState.sessionId}/complete`, {
            method: 'POST'
        });
        
        const data = await response.json();
        
        appState.sessionStatus = 'COMPLETED';
        updateSessionStatusUI();
        
        const winner = data.winner;
        const feedback = data.feedback;
        
        showToast(
            t('messages.debateComplete')
                .replace('{winner}', winner)
                .replace('{userScore}', data.finalScoreUser)
                .replace('{aiScore}', data.finalScoreAI)
                .replace('{feedback}', feedback.overall_assessment),
            'success'
        );
        
        // Stop timer
        stopTimer();
        
        // Disable input
        document.getElementById('argument-input').disabled = true;
        document.getElementById('btn-submit').disabled = true;
        document.getElementById('btn-preview').disabled = true;
        
        // Show new debate button
        document.getElementById('new-debate-section').classList.remove('hidden');
    } catch (error) {
        console.error('Error completing debate:', error);
    }
}

function resetDebate() {
    appState.sessionId = null;
    appState.sessionStatus = 'NOT_STARTED';
    appState.chatMessages = [];
    appState.currentRound = 1;
    appState.scores = { userTotal: 0, aiTotal: 0 };
    
    document.getElementById('argument-input').disabled = false;
    document.getElementById('argument-input').value = '';
    document.getElementById('btn-submit').disabled = false;
    document.getElementById('btn-preview').disabled = false;
    document.getElementById('new-debate-section').classList.add('hidden');
    document.getElementById('selected-topic-display').textContent = t('topics.noTopics');
    document.getElementById('chat-empty-state').style.display = 'block';
    document.getElementById('chat-messages').innerHTML = '<div id="chat-empty-state" class="text-center text-gray-500 italic py-8"><span id="chat-empty-text">' + t('chat.emptyState') + '</span></div>';
    
    stopTimer();
    closeStreamingSSE();
    
    updateSessionStatusUI();
    updateUI();
    
    // Show topic selector
    openTopicModal();
}

// ========== UI Updates ==========
function updateUI() {
    // Update round display
    document.getElementById('current-round-num').textContent = appState.currentRound;
    const progress = (appState.currentRound / appState.maxRounds) * 100;
    document.getElementById('round-progress').style.width = `${progress}%`;
    
    // Update flow steps
    for (let i = 1; i <= 5; i++) {
        const flowStep = document.getElementById(`flow-round${i}`);
        flowStep.classList.remove('active', 'completed');
        if (i < appState.currentRound) {
            flowStep.classList.add('completed');
        } else if (i === appState.currentRound) {
            flowStep.classList.add('active');
        }
    }
    
    if (appState.currentRound > 5) {
        document.getElementById('flow-judging').classList.add('active');
    }
    
    // Update scores
    updateScores();
}

function updateScores() {
    document.getElementById('user-score').textContent = appState.scores.userTotal.toFixed(2);
    document.getElementById('ai-score').textContent = appState.scores.aiTotal.toFixed(2);
}

function updateSessionStatusUI() {
    const statusText = document.getElementById('status-text');
    statusText.textContent = t('states.' + appState.sessionStatus.toLowerCase().replace('_', ''));
    
    const statusDiv = document.getElementById('session-status');
    statusDiv.className = 'text-sm px-3 py-1 rounded border';
    
    if (appState.sessionStatus === 'IN_PROGRESS') {
        statusDiv.classList.add('bg-green-900', 'border-green-500', 'text-green-300');
    } else if (appState.sessionStatus === 'COMPLETED') {
        statusDiv.classList.add('bg-blue-900', 'border-blue-500', 'text-blue-300');
    } else {
        statusDiv.classList.add('bg-wasteland-darker', 'border-wasteland-gold', 'text-gray-400');
    }
}

function updateCharCounter() {
    const input = document.getElementById('argument-input');
    const counter = document.getElementById('char-counter');
    if (input && counter) {
        counter.textContent = `${input.value.length} / 500 ${t('debate.charCounter')}`;
    }
}

// ========== Timer ==========
function startTimer() {
    appState.timeRemaining = 180;
    updateTimerDisplay();
    
    appState.timerInterval = setInterval(() => {
        appState.timeRemaining--;
        updateTimerDisplay();
        
        if (appState.timeRemaining <= 0) {
            stopTimer();
        }
    }, 1000);
}

function stopTimer() {
    if (appState.timerInterval) {
        clearInterval(appState.timerInterval);
        appState.timerInterval = null;
    }
}

function updateTimerDisplay() {
    const minutes = Math.floor(appState.timeRemaining / 60);
    const seconds = appState.timeRemaining % 60;
    document.getElementById('time-display').textContent = 
        `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

// ========== Modal Management ==========
function openTopicModal() {
    loadTopics();
    document.getElementById('topic-modal').classList.remove('hidden');
    document.getElementById('topic-modal').classList.add('flex');
}

function closeTopicModal() {
    document.getElementById('topic-modal').classList.add('hidden');
    document.getElementById('topic-modal').classList.remove('flex');
}

// ========== Utilities ==========
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showToast(message, type = 'info') {
    alert(message);
}

// ========== Event Listeners ==========
window.addEventListener('DOMContentLoaded', async () => {
    await loadTranslations();
    
    // Language toggle
    document.getElementById('btn-lang-toggle').addEventListener('click', toggleLanguage);
    
    // Topic selection
    document.getElementById('btn-select-topic').addEventListener('click', openTopicModal);
    document.getElementById('btn-close-modal').addEventListener('click', closeTopicModal);
    
    // Debate actions
    document.getElementById('btn-submit').addEventListener('click', submitArgument);
    document.getElementById('btn-preview').addEventListener('click', previewArgument);
    document.getElementById('argument-input').addEventListener('input', updateCharCounter);
    
    // New debate
    document.getElementById('btn-new-debate').addEventListener('click', resetDebate);
    
    // Initial UI update
    updateUI();
    updateSessionStatusUI();
});
